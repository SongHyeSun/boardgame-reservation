package com.boardgame.reservation.boardgame;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.event.BoardGameSuspendedEvent;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.party.domain.PartyStatus;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게임 숨기기(운영 중지)/다시 보이기를 실제 Redis(Testcontainers) + Security 필터 체인으로 검증.
 * 한 트랜잭션에서 게임 숨김 + RECRUITING 파티 CANCELLED, 커밋 후 Redis 키 삭제, 이벤트 발행까지 확인한다.
 */
@RecordApplicationEvents
class BoardGameVisibilityIntegrationTest extends RedisIntegrationTestSupport {

    @Autowired
    WebApplicationContext context;
    @Autowired
    ApplicationEvents applicationEvents;

    MockMvc mockMvc;
    Member admin;
    Member otherAdmin;
    Member superAdmin;
    Member host;
    Member guest;
    BoardGame game;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        admin = saveAdmin("admin");
        otherAdmin = saveAdmin("other");
        superAdmin = memberRepository.save(Member.createSuperAdmin("root@test.com", "pw", "root"));
        host = saveMember("host");
        guest = saveMember("guest");
        game = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Catan", 2, 4, 60, Difficulty.NORMAL, "설명", true, false, 1), admin));
    }

    // ───────────── fixture ─────────────

    private long createParty(Member partyHost) throws Exception {
        String json = mockMvc.perform(post("/api/parties").with(loginAs(partyHost))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardGameId":%d,"title":"같이 해요","description":"초보 환영","capacity":4}
                                """.formatted(game.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    private void join(long partyId, Member member) throws Exception {
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(member)))
                .andExpect(status().isOk());
    }

    private ResultActions setVisible(Member actor, boolean visible) throws Exception {
        return mockMvc.perform(patch("/api/boardgames/{id}/visibility", game.getId()).with(loginAs(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":" + visible + "}"));
    }

    private PartyStatus statusOf(long partyId) {
        return partyRepository.findById(partyId).orElseThrow().getStatus();
    }

    private boolean hasKeys(long partyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(remainingKey(partyId)))
                || Boolean.TRUE.equals(redisTemplate.hasKey(membersKey(partyId)));
    }

    private static String remainingKey(long partyId) {
        return "party:" + partyId + ":remaining";
    }

    private static String membersKey(long partyId) {
        return "party:" + partyId + ":members";
    }

    // ───────────── 숨기기 ─────────────

    @Test
    @DisplayName("⭐ 숨기면 RECRUITING 파티는 CANCELLED, CLOSED 는 유지, 취소된 파티의 Redis 키는 삭제되고 이벤트가 발행된다")
    void hide_cancelsRecruitingParties() throws Exception {
        long recruitingA = createParty(host);
        join(recruitingA, guest);
        long recruitingB = createParty(host);
        long closed = createParty(host);
        mockMvc.perform(patch("/api/parties/{id}/close", closed).with(loginAs(host)))
                .andExpect(status().isOk());
        assertThat(hasKeys(recruitingA)).isTrue();
        assertThat(hasKeys(recruitingB)).isTrue();

        setVisible(admin, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(game.getId()))
                .andExpect(jsonPath("$.data.visible").value(false));

        assertThat(statusOf(recruitingA)).isEqualTo(PartyStatus.CANCELLED);
        assertThat(statusOf(recruitingB)).isEqualTo(PartyStatus.CANCELLED);
        assertThat(statusOf(closed)).isEqualTo(PartyStatus.CLOSED);
        // 커밋 후 삭제: remaining·members 두 키 모두
        assertThat(redisTemplate.hasKey(remainingKey(recruitingA))).isFalse();
        assertThat(redisTemplate.hasKey(membersKey(recruitingA))).isFalse();
        assertThat(hasKeys(recruitingB)).isFalse();
        // 참여 이력은 그대로
        assertThat(partyMemberRepository.countByPartyId(recruitingA)).isEqualTo(2);

        assertThat(applicationEvents.stream(BoardGameSuspendedEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.gameId()).isEqualTo(game.getId());
                    assertThat(event.cancelledPartyIds()).containsExactlyInAnyOrder(recruitingA, recruitingB);
                });
    }

    @Test
    @DisplayName("숨긴 뒤: 기본 목록에서 제외, 상세는 visible:false 로 조회, 내 게임(mine)에는 남고, 파티 응답에 boardGameVisible=false")
    void hide_thenReads() throws Exception {
        long recruiting = createParty(host);
        long closed = createParty(host);
        mockMvc.perform(patch("/api/parties/{id}/close", closed).with(loginAs(host)))
                .andExpect(status().isOk());

        setVisible(admin, false).andExpect(status().isOk());

        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data").value(empty()));
        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(false));
        mockMvc.perform(get("/api/boardgames").param("mine", "true").with(loginAs(admin)))
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan")))
                .andExpect(jsonPath("$.data[0].visible").value(false));

        // 기본 파티 목록(RECRUITING)에는 안 나오고, 취소·마감 이력에는 「운영 중지」 배지용 플래그가 내려온다
        mockMvc.perform(get("/api/parties").param("status", "RECRUITING"))
                .andExpect(jsonPath("$.data").value(empty()));
        mockMvc.perform(get("/api/parties").param("status", "CANCELLED"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(recruiting))
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[0].boardGameVisible").value(false));
        mockMvc.perform(get("/api/parties/{id}", closed))
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.boardGameVisible").value(false));
    }

    @Test
    @DisplayName("숨긴 게임으로는 파티를 개설할 수 없다 (409 운영이 중지된 게임입니다)")
    void hide_thenCreatePartyConflict() throws Exception {
        setVisible(admin, false).andExpect(status().isOk());

        mockMvc.perform(post("/api/parties").with(loginAs(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"boardGameId":%d,"title":"새 파티","capacity":4}
                                """.formatted(game.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("운영이 중지된 게임입니다."));

        assertThat(partyRepository.count()).isZero();
    }

    @Test
    @DisplayName("취소된 파티에는 참여할 수 없고(409), 호스트가 close 해도 CANCELLED 가 CLOSED 로 덮이지 않는다")
    void hide_thenCancelledPartyIsFrozen() throws Exception {
        long partyId = createParty(host);
        setVisible(admin, false).andExpect(status().isOk());

        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("모집 중인 파티가 아닙니다."));
        mockMvc.perform(patch("/api/parties/{id}/close", partyId).with(loginAs(host)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("모집 중인 파티가 아닙니다."));

        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.CANCELLED);
        assertThat(hasKeys(partyId)).isFalse();
    }

    @Test
    @DisplayName("이미 숨긴 게임을 또 숨겨도 200 이고 이벤트는 한 번만 발행된다")
    void hide_isIdempotent() throws Exception {
        createParty(host);

        setVisible(admin, false).andExpect(status().isOk());
        setVisible(admin, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(false));

        assertThat(applicationEvents.stream(BoardGameSuspendedEvent.class).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 보이면 게임만 노출된다: 취소된 파티는 복구되지 않고, 새 파티는 개설할 수 있다")
    void show_doesNotRestoreParties() throws Exception {
        long cancelled = createParty(host);
        setVisible(admin, false).andExpect(status().isOk());

        setVisible(admin, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(true));

        assertThat(statusOf(cancelled)).isEqualTo(PartyStatus.CANCELLED);
        assertThat(hasKeys(cancelled)).isFalse();
        mockMvc.perform(get("/api/boardgames"))
                .andExpect(jsonPath("$.data[*].name").value(contains("Catan")));
        mockMvc.perform(get("/api/parties").param("status", "RECRUITING"))
                .andExpect(jsonPath("$.data").value(empty()));
        // 보이기는 이벤트를 발행하지 않는다 (숨김 1회분만)
        assertThat(applicationEvents.stream(BoardGameSuspendedEvent.class).count()).isEqualTo(1);

        createParty(host);
        assertThat(partyRepository.count()).isEqualTo(2);
    }

    // ───────────── 권한 · 입력 ─────────────

    @Test
    @DisplayName("⭐ 소유자가 아니면(다른 ADMIN, SUPER_ADMIN) 숨길 수 없다: 403, 게임·파티·Redis 키·이벤트 모두 그대로")
    void hide_notOwner_forbidden() throws Exception {
        long partyId = createParty(host);

        for (Member intruder : new Member[]{otherAdmin, superAdmin}) {
            setVisible(intruder, false)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("본인이 등록한 게임만 관리할 수 있습니다."));
        }

        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(jsonPath("$.data.visible").value(true));
        assertThat(statusOf(partyId)).isEqualTo(PartyStatus.RECRUITING);
        assertThat(hasKeys(partyId)).isTrue();
        assertThat(applicationEvents.stream(BoardGameSuspendedEvent.class).count()).isZero();
    }

    @Test
    @DisplayName("다시 보이기도 소유자만 할 수 있다")
    void show_notOwner_forbidden() throws Exception {
        setVisible(admin, false).andExpect(status().isOk());

        setVisible(otherAdmin, true).andExpect(status().isForbidden());

        mockMvc.perform(get("/api/boardgames/{id}", game.getId()))
                .andExpect(jsonPath("$.data.visible").value(false));
    }

    @Test
    @DisplayName("없는 게임은 404, visible 이 없는 본문은 400")
    void hide_notFound_and_invalidBody() throws Exception {
        mockMvc.perform(patch("/api/boardgames/{id}/visibility", 999999L).with(loginAs(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/boardgames/{id}/visibility", game.getId()).with(loginAs(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
