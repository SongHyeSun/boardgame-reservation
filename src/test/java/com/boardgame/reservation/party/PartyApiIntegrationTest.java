package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.member.domain.Member;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 파티 API 를 Security 필터 체인 + 실제 Redis(Testcontainers) + H2 로 검증.
 * 보드게임은 2~4명이라 capacity 2 면 호스트 + 1명으로 꽉 찬다.
 */
class PartyApiIntegrationTest extends PartyRedisTestSupport {

    @Autowired
    WebApplicationContext context;
    @Autowired
    SessionRepositoryFilter<?> sessionRepositoryFilter;

    MockMvc mockMvc;
    Member host;
    Member guest;
    Member other;
    BoardGame boardGame;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        host = saveMember("host");
        guest = saveMember("guest");
        other = saveMember("other");
        boardGame = saveBoardGame(2, 4);
    }

    /** 컨트롤러가 @AuthenticationPrincipal MemberPrincipal 을 쓰므로 실제 주체 타입으로 로그인 상태를 만든다 */
    private static RequestPostProcessor loginAs(Member member) {
        MemberPrincipal principal = MemberPrincipal.from(member);
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
    }

    private String body(int capacity) {
        return """
                {"boardGameId":%d,"title":"같이 해요","description":"초보 환영","capacity":%d}
                """.formatted(boardGame.getId(), capacity);
    }

    private long createParty(int capacity) throws Exception {
        String json = mockMvc.perform(post("/api/parties")
                        .with(loginAs(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(capacity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.id")).longValue();
    }

    // ───────────── 개설 ─────────────

    @Test
    @DisplayName("개설하면 201, 호스트가 첫 참여자로 들어가고 Redis remaining 은 capacity-1")
    void create_success() throws Exception {
        long partyId = createParty(4);

        mockMvc.perform(get("/api/parties/{id}", partyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RECRUITING"))
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(jsonPath("$.data.remaining").value(3))
                .andExpect(jsonPath("$.data.hostNickname").value("host"))
                .andExpect(jsonPath("$.data.members.length()").value(1))
                .andExpect(jsonPath("$.data.members[0].nickname").value("host"));

        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("3");
    }

    @Test
    @DisplayName("capacity 가 보드게임 인원 범위를 벗어나면 400, 파티는 생기지 않는다")
    void create_invalidCapacity() throws Exception {
        mockMvc.perform(post("/api/parties")
                        .with(loginAs(host))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertThat(partyRepository.count()).isZero();
    }

    @Test
    @DisplayName("비로그인 개설/참여는 401")
    void unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(4)))
                .andExpect(status().isUnauthorized());

        long partyId = createParty(4);
        mockMvc.perform(post("/api/parties/{id}/join", partyId))
                .andExpect(status().isUnauthorized());
    }

    // ───────────── 참여 ─────────────

    @Test
    @DisplayName("참여하면 200 + remaining, 상세에 참여자가 반영된다")
    void join_success() throws Exception {
        long partyId = createParty(4);

        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(2));

        mockMvc.perform(get("/api/parties/{id}", partyId))
                .andExpect(jsonPath("$.data.remaining").value(2))
                .andExpect(jsonPath("$.data.members.length()").value(2));
    }

    @Test
    @DisplayName("같은 회원이 다시 참여하면 409 '이미 참여한 파티입니다'")
    void join_duplicate() throws Exception {
        long partyId = createParty(4);
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 참여한 파티입니다"));

        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("호스트가 다시 join 해도 409 (개설 시 이미 참여자)")
    void join_hostAlreadyJoined() throws Exception {
        long partyId = createParty(4);

        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(host)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 참여한 파티입니다"));
    }

    @Test
    @DisplayName("정원이 차면 409 '정원이 마감되었습니다', 상태는 RECRUITING 유지")
    void join_full() throws Exception {
        long partyId = createParty(2);
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(0));

        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정원이 마감되었습니다"));

        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(2);
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("0");
        mockMvc.perform(get("/api/parties/{id}", partyId))
                .andExpect(jsonPath("$.data.status").value("RECRUITING"));
    }

    @Test
    @DisplayName("없는 파티에 참여하면 404")
    void join_notFound() throws Exception {
        mockMvc.perform(post("/api/parties/{id}/join", 999999L).with(loginAs(guest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Redis 키가 사라져도 join 시 DB 기준으로 복구된다")
    void join_recoversAfterRedisLoss() throws Exception {
        long partyId = createParty(3);
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isOk());
        redisTemplate.delete(redisTemplate.keys("party:*")); // Redis 재시작 시뮬레이션

        // 남은 자리 3 - 2(호스트, guest) = 1
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(0));

        // 복구된 members 게이트가 기존 참여자의 재참여도 막는다
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isConflict());
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(3);
    }

    // ───────────── 취소 / 마감 ─────────────

    @Test
    @DisplayName("참여 취소하면 자리가 돌아오고 다시 참여할 수 있다")
    void leave_success() throws Exception {
        long partyId = createParty(2);
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/parties/{id}/leave", partyId).with(loginAs(guest)))
                .andExpect(status().isOk());

        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("1");
        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(other)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("호스트 탈퇴 400, 미참여자 탈퇴 400")
    void leave_hostOrNotJoined() throws Exception {
        long partyId = createParty(4);

        mockMvc.perform(delete("/api/parties/{id}/leave", partyId).with(loginAs(host)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/parties/{id}/leave", partyId).with(loginAs(other)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("호스트가 close 하면 200, 이후 join 은 409 이고 Redis 키가 삭제된다")
    void close_thenJoinConflict() throws Exception {
        long partyId = createParty(4);

        mockMvc.perform(patch("/api/parties/{id}/close", partyId).with(loginAs(host)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(guest)))
                .andExpect(status().isConflict());
        assertThat(redisTemplate.keys("party:*")).isEmpty();
        mockMvc.perform(get("/api/parties/{id}", partyId))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @DisplayName("호스트가 아니면 close 403")
    void close_nonHost_403() throws Exception {
        long partyId = createParty(4);

        mockMvc.perform(patch("/api/parties/{id}/close", partyId).with(loginAs(guest)))
                .andExpect(status().isForbidden());
    }

    // ───────────── 목록 ─────────────

    @Test
    @DisplayName("목록은 비로그인도 조회 가능하고 status/boardGameId 로 필터되며 currentCount 를 포함한다")
    void list_filters() throws Exception {
        long recruiting = createParty(4);
        mockMvc.perform(post("/api/parties/{id}/join", recruiting).with(loginAs(guest)))
                .andExpect(status().isOk());
        long closed = createParty(3);
        mockMvc.perform(patch("/api/parties/{id}/close", closed).with(loginAs(host)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/parties").param("status", "RECRUITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(recruiting))
                .andExpect(jsonPath("$.data[0].boardGameName").value("Catan"))
                .andExpect(jsonPath("$.data[0].hostNickname").value("host"))
                .andExpect(jsonPath("$.data[0].currentCount").value(2));

        mockMvc.perform(get("/api/parties").param("boardGameId", String.valueOf(boardGame.getId())))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/parties").param("boardGameId", "999999"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ───────────── 세션 (Spring Session + Redis) ─────────────

    @Test
    @DisplayName("로그인 세션은 Redis 에 저장되고, 세션 쿠키만으로 인증이 복원된다 (Redis 키를 지우면 401)")
    void login_sessionStoredInRedis() throws Exception {
        // 기존 mockMvc 에는 세션 필터가 없다 → SessionRepositoryFilter 를 Security 필터보다 앞에 둔다
        MockMvc sessionMockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(sessionRepositoryFilter)
                .apply(springSecurity())
                .build();
        sessionMockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"session@test.com","password":"password123","nickname":"세션"}
                                """))
                .andExpect(status().isCreated());
        assertThat(redisTemplate.keys("spring:session:*")).isEmpty();

        MvcResult login = sessionMockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"session@test.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        Cookie[] cookies = login.getResponse().getCookies();
        assertThat(cookies).hasSize(1);
        Cookie sessionCookie = cookies[0];
        // Spring Session 쿠키 값은 세션 ID 를 Base64 로 인코딩한 것
        String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
        String redisKey = "spring:session:sessions:" + sessionId;

        assertThat(redisTemplate.keys("spring:session:*")).containsExactly(redisKey);
        assertThat(redisTemplate.opsForHash().keys(redisKey)).contains("sessionAttr:SPRING_SECURITY_CONTEXT");

        // 새 요청은 쿠키만 가지고 있다 → Redis 에서 역직렬화된 SecurityContext 로 인증
        sessionMockMvc.perform(get("/api/members/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("session@test.com"));

        // 세션의 원본이 Redis 임을 확인: 키를 지우면 같은 쿠키로도 인증되지 않는다
        redisTemplate.delete(redisKey);
        sessionMockMvc.perform(get("/api/members/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    // ───────────── 보드게임 삭제 ─────────────

    @Test
    @DisplayName("파티가 있는 보드게임을 삭제하면 409 BOARDGAME_IN_USE")
    void deleteBoardGame_inUse() throws Exception {
        createParty(4);

        mockMvc.perform(delete("/api/boardgames/{id}", boardGame.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());

        assertThat(boardGameRepository.existsById(boardGame.getId())).isTrue();
    }
}
