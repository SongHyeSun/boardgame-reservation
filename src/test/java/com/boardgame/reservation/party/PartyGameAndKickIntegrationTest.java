package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.party.domain.PartyMemberStatus;
import com.boardgame.reservation.party.event.PartyMemberKickedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기타 게임 파티 · 진행 방식(접속 링크 공개 범위) · 참여자 내보내기를
 * Security 필터 체인 + 실제 Redis(Testcontainers) + H2 로 검증한다.
 * 기본 보드게임(boardGame)은 2~4명 오프라인 전용이다.
 */
@RecordApplicationEvents
class PartyGameAndKickIntegrationTest extends PartyApiTestSupport {

    private static final String LINK = "https://discord.gg/abc";

    @Autowired
    ApplicationEvents applicationEvents;

    // ───────────── fixture ─────────────

    private static String customBody(int capacity, String playMode) {
        return """
                {"customGameName":"구스구스덕","title":"오리 잡자","capacity":%d,"playMode":"%s"}
                """.formatted(capacity, playMode);
    }

    /** 기타 게임 온라인 파티 (플랫폼·접속 링크 포함) */
    private static String customOnlineBody(int capacity) {
        return """
                {"customGameName":"구스구스덕","title":"오리 잡자","capacity":%d,"playMode":"ONLINE",
                 "onlinePlatform":"디스코드","onlineLink":"%s"}
                """.formatted(capacity, LINK);
    }

    private String boardGameBody(long boardGameId, int capacity, String playMode) {
        return """
                {"boardGameId":%d,"title":"카탄 하실 분","capacity":%d,"playMode":"%s"}
                """.formatted(boardGameId, capacity, playMode);
    }

    private BoardGame saveBothModesGame() {
        return boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Arena", 2, 4, 60, Difficulty.NORMAL, "온·오프라인", true, true, 1), null));
    }

    /** viewer 가 null 이면 비로그인으로 상세 조회 */
    private ResultActions detail(Member viewer, long partyId) throws Exception {
        var request = get("/api/parties/{id}", partyId);
        return mockMvc.perform(viewer == null ? request : request.with(loginAs(viewer)));
    }

    private ResultActions kick(Member actor, long partyId, Member target) throws Exception {
        return mockMvc.perform(delete("/api/parties/{id}/members/{memberId}", partyId, target.getId())
                .with(loginAs(actor)));
    }

    private ResultActions joinRequest(Member member, long partyId) throws Exception {
        return mockMvc.perform(post("/api/parties/{id}/join", partyId).with(loginAs(member)));
    }

    private ResultActions postParty(Member creator, String json) throws Exception {
        return mockMvc.perform(post("/api/parties")
                .with(loginAs(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    // ───────────── 기타 게임 파티 ─────────────

    @Test
    @DisplayName("⭐ 기타 게임 파티를 개설하면 201, 목록·상세에 나온다 (left join 확인) — boardGameId 는 null")
    void customGame_createListDetail() throws Exception {
        long boardGameParty = createPartyAs(host, boardGameBody(boardGame.getId(), 4, "OFFLINE"));

        postParty(host, customOnlineBody(8))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.boardGameId").doesNotExist())
                .andExpect(jsonPath("$.data.gameName").value("구스구스덕"))
                .andExpect(jsonPath("$.data.customGame").value(true))
                .andExpect(jsonPath("$.data.boardGameVisible").value(true))
                .andExpect(jsonPath("$.data.playMode").value("ONLINE"))
                .andExpect(jsonPath("$.data.currentCount").value(1));
        long customParty = partyRepository.findAll().stream()
                .filter(p -> p.isCustomGame()).findFirst().orElseThrow().getId();

        // 목록: 기타 게임 파티가 사라지지 않고(inner join 이면 누락) 보드게임 파티와 함께 나온다 (최신순)
        mockMvc.perform(get("/api/parties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(customParty))
                .andExpect(jsonPath("$.data[0].gameName").value("구스구스덕"))
                .andExpect(jsonPath("$.data[0].customGame").value(true))
                .andExpect(jsonPath("$.data[0].boardGameId").doesNotExist())
                .andExpect(jsonPath("$.data[0].boardGameVisible").value(true))
                .andExpect(jsonPath("$.data[0].hostNickname").value("host"))
                .andExpect(jsonPath("$.data[1].id").value(boardGameParty))
                .andExpect(jsonPath("$.data[1].gameName").value("Catan"))
                .andExpect(jsonPath("$.data[1].customGame").value(false))
                .andExpect(jsonPath("$.data[1].boardGameId").value(boardGame.getId()));

        // 상세: close·leave·kick 도 쓰는 findWithDetailsById(left join fetch) 경로
        detail(null, customParty)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameName").value("구스구스덕"))
                .andExpect(jsonPath("$.data.customGame").value(true))
                .andExpect(jsonPath("$.data.boardGameId").doesNotExist())
                .andExpect(jsonPath("$.data.capacity").value(8))
                .andExpect(jsonPath("$.data.remaining").value(7))
                .andExpect(jsonPath("$.data.members.length()").value(1));
        assertThat(redisTemplate.opsForValue().get(remainingKey(customParty))).isEqualTo("7");
    }

    @Test
    @DisplayName("boardGameId 필터는 기타 게임 파티를 제외하고, playMode 필터는 진행 방식으로 거른다")
    void list_boardGameIdAndPlayModeFilters() throws Exception {
        long offlineBoardParty = createPartyAs(host, boardGameBody(boardGame.getId(), 4, "OFFLINE"));
        long onlineCustomParty = createPartyAs(host, customOnlineBody(6));
        long offlineCustomParty = createPartyAs(host, customBody(6, "OFFLINE"));

        mockMvc.perform(get("/api/parties").param("boardGameId", String.valueOf(boardGame.getId())))
                .andExpect(jsonPath("$.data[*].id").value(contains((int) offlineBoardParty)));
        mockMvc.perform(get("/api/parties").param("playMode", "ONLINE"))
                .andExpect(jsonPath("$.data[*].id").value(contains((int) onlineCustomParty)));
        mockMvc.perform(get("/api/parties").param("playMode", "OFFLINE"))
                .andExpect(jsonPath("$.data[*].id").value(contains((int) offlineCustomParty, (int) offlineBoardParty)));
        mockMvc.perform(get("/api/parties").param("playMode", "ONLINE").param("boardGameId", String.valueOf(boardGame.getId())))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("기타 게임 파티도 선착순 참여가 동작한다 (정원 2 → 호스트 + 1명, 다음 사람은 409)")
    void customGame_joinAndFull() throws Exception {
        long partyId = createPartyAs(host, customBody(2, "OFFLINE"));

        joinRequest(guest, partyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(0));
        joinRequest(other, partyId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정원이 마감되었습니다"));

        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("잘못된 개설 요청은 400 이고 파티가 생기지 않는다 (둘 다/둘 다 없음, 정원 범위, 방식 미지원, 링크 형식 …)")
    void create_invalidRequests_400() throws Exception {
        String gameId = String.valueOf(boardGame.getId());
        List<String> invalid = List.of(
                // 게임 종류: 둘 다 / 둘 다 없음 / 공백 이름
                """
                {"boardGameId":%s,"customGameName":"롤","title":"t","capacity":4,"playMode":"OFFLINE"}
                """.formatted(gameId),
                """
                {"title":"t","capacity":4,"playMode":"OFFLINE"}
                """,
                """
                {"customGameName":"   ","title":"t","capacity":4,"playMode":"OFFLINE"}
                """,
                // 기타 게임 정원은 2~20
                customBody(1, "OFFLINE"),
                customBody(21, "OFFLINE"),
                // 이름 51자
                """
                {"customGameName":"%s","title":"t","capacity":4,"playMode":"OFFLINE"}
                """.formatted("가".repeat(51)),
                // 진행 방식 누락 / 오프라인 전용 보드게임을 ONLINE 으로
                """
                {"customGameName":"롤","title":"t","capacity":4}
                """,
                boardGameBody(boardGame.getId(), 4, "ONLINE"),
                // 접속 링크는 http:// / https:// 만
                """
                {"customGameName":"롤","title":"t","capacity":4,"playMode":"ONLINE","onlineLink":"ftp://x.com"}
                """,
                """
                {"customGameName":"롤","title":"t","capacity":4,"playMode":"ONLINE","onlineLink":"javascript:alert(1)"}
                """);

        for (String json : invalid) {
            postParty(host, json)
                    .andExpect(status().isBadRequest());
        }
        assertThat(partyRepository.count()).isZero();
    }

    @Test
    @DisplayName("에러 메시지: 게임 선택 오류 / 방식 미지원 / 접속 링크 형식")
    void create_invalidRequests_messages() throws Exception {
        postParty(host, """
                {"title":"t","capacity":4,"playMode":"OFFLINE"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보드게임을 선택하거나 게임 이름을 입력해주세요."));
        postParty(host, boardGameBody(boardGame.getId(), 4, "ONLINE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이 게임은 해당 방식으로 진행할 수 없습니다."));
        postParty(host, """
                {"customGameName":"롤","title":"t","capacity":4,"playMode":"ONLINE","onlineLink":"ftp://x.com"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("http:// 또는 https:// 링크만 입력할 수 있습니다."));
    }

    @Test
    @DisplayName("온라인·오프라인을 모두 지원하는 보드게임은 두 방식 모두로 개설되고, 방식에 맞지 않는 필드는 버려진다")
    void boardGame_bothModes_dropsMismatchedFields() throws Exception {
        BoardGame both = saveBothModesGame();

        long online = createPartyAs(host, """
                {"boardGameId":%d,"title":"온라인","capacity":4,"playMode":"ONLINE","onlinePlatform":"보드게임아레나",
                 "onlineLink":"%s","location":"동아리방"}
                """.formatted(both.getId(), LINK));
        long offline = createPartyAs(host, """
                {"boardGameId":%d,"title":"오프라인","capacity":4,"playMode":"OFFLINE","onlinePlatform":"보드게임아레나",
                 "onlineLink":"%s","location":" 동아리방 "}
                """.formatted(both.getId(), LINK));

        detail(host, online)
                .andExpect(jsonPath("$.data.playMode").value("ONLINE"))
                .andExpect(jsonPath("$.data.onlinePlatform").value("보드게임아레나"))
                .andExpect(jsonPath("$.data.onlineLink").value(LINK))
                .andExpect(jsonPath("$.data.location").doesNotExist());
        detail(host, offline)
                .andExpect(jsonPath("$.data.playMode").value("OFFLINE"))
                .andExpect(jsonPath("$.data.location").value("동아리방"))
                .andExpect(jsonPath("$.data.onlinePlatform").doesNotExist())
                .andExpect(jsonPath("$.data.onlineLink").doesNotExist());
    }

    // ───────────── 접속 링크 공개 범위 ─────────────

    @Test
    @DisplayName("⭐ onlineLink 는 호스트·참여자에게만: 비로그인·미참여 null, 참여자·호스트 값, 내보내진 회원 null (플랫폼은 모두에게 공개)")
    void onlineLink_visibleOnlyToHostAndParticipants() throws Exception {
        long partyId = createPartyAs(host, customOnlineBody(4));
        joinAs(guest, partyId);

        // 비로그인 / 로그인했지만 미참여: 링크 숨김, 플랫폼·방식은 공개
        for (Member viewer : new Member[]{null, other}) {
            detail(viewer, partyId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.onlineLink").doesNotExist())
                    .andExpect(jsonPath("$.data.onlinePlatform").value("디스코드"))
                    .andExpect(jsonPath("$.data.playMode").value("ONLINE"));
        }
        // 호스트, 참여자: 링크 노출
        for (Member viewer : new Member[]{host, guest}) {
            detail(viewer, partyId)
                    .andExpect(jsonPath("$.data.onlineLink").value(LINK));
        }

        // 내보내면 그 회원에게서 링크가 다시 사라진다
        kick(host, partyId, guest).andExpect(status().isOk());
        detail(guest, partyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlineLink").doesNotExist());
        detail(host, partyId)
                .andExpect(jsonPath("$.data.onlineLink").value(LINK));
    }

    // ───────────── 참여자 내보내기 ─────────────

    @Test
    @DisplayName("⭐ 호스트가 내보내면 200: 자리 +1, COUNT·목록에서 제외, 이벤트 발행, 재참여 403, 다른 회원은 그 자리에 참여 가능")
    void kick_success() throws Exception {
        long partyId = createPartyAs(host, customBody(3, "OFFLINE"));
        joinAs(guest, partyId);
        joinAs(other, partyId);
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("0");
        Member third = saveMember("third");
        joinRequest(third, partyId).andExpect(status().isConflict()); // 꽉 참

        kick(host, partyId, guest).andExpect(status().isOk());

        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("1");
        assertThat(redisTemplate.opsForSet().isMember(membersKey(partyId), String.valueOf(guest.getId()))).isFalse();
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(2);
        detail(null, partyId)
                .andExpect(jsonPath("$.data.remaining").value(1))
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.members[*].nickname").value(contains("host", "other")));
        mockMvc.perform(get("/api/parties"))
                .andExpect(jsonPath("$.data[0].currentCount").value(2));
        assertThat(applicationEvents.stream(PartyMemberKickedEvent.class).toList())
                .containsExactly(new PartyMemberKickedEvent(partyId, guest.getId()));

        // 내보낸 회원은 다시 참여할 수 없다 (자리가 비어 있어도)
        joinRequest(guest, partyId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("파티장이 내보낸 파티에는 다시 참여할 수 없습니다."));
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("1");

        // 다른 회원은 그 자리에 참여할 수 있다
        joinRequest(third, partyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(0));
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(3);
    }

    @Test
    @DisplayName("내보낸 행은 KICKED 로 남고, 내보내진 회원은 leave 로 그 행을 지워 재참여할 수 없다 (leave 400, 재참여는 계속 403)")
    void kick_keepsRow_leaveCannotBypass() throws Exception {
        long partyId = createPartyAs(host, customBody(4, "OFFLINE"));
        joinAs(guest, partyId);
        kick(host, partyId, guest).andExpect(status().isOk());

        mockMvc.perform(delete("/api/parties/{id}/leave", partyId).with(loginAs(guest)))
                .andExpect(status().isBadRequest());

        assertThat(partyMemberRepository.existsByPartyIdAndMemberIdAndStatus(
                partyId, guest.getId(), PartyMemberStatus.KICKED)).isTrue();
        joinRequest(guest, partyId).andExpect(status().isForbidden());
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("3");
    }

    @Test
    @DisplayName("자진 탈퇴(leave)는 행을 지워 재참여할 수 있다 — 내보내기와 다르다")
    void leave_stillAllowsRejoin() throws Exception {
        long partyId = createPartyAs(host, customBody(4, "OFFLINE"));
        joinAs(guest, partyId);
        mockMvc.perform(delete("/api/parties/{id}/leave", partyId).with(loginAs(guest)))
                .andExpect(status().isOk());

        joinRequest(guest, partyId).andExpect(status().isOk());
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("내보내기 규칙: 비호스트 403, 호스트 자신 400, 참여 중이 아닌 회원 400, 마감된 파티 409, 없는 파티 404, 비로그인 401")
    void kick_rules() throws Exception {
        long partyId = createPartyAs(host, customBody(4, "OFFLINE"));
        joinAs(guest, partyId);
        joinAs(other, partyId);

        kick(guest, partyId, other).andExpect(status().isForbidden());
        kick(host, partyId, host)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("호스트는 내보낼 수 없습니다."));
        kick(host, partyId, saveMember("stranger")).andExpect(status().isBadRequest());
        kick(host, 999999L, guest).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/parties/{id}/members/{memberId}", partyId, guest.getId()))
                .andExpect(status().isUnauthorized());
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(3);
        assertThat(applicationEvents.stream(PartyMemberKickedEvent.class)).isEmpty();

        mockMvc.perform(patch("/api/parties/{id}/close", partyId).with(loginAs(host)))
                .andExpect(status().isOk());
        kick(host, partyId, guest).andExpect(status().isConflict());
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(3);
    }

    @Test
    @DisplayName("⭐ Redis 키가 사라져도 복구는 KICKED 를 제외한다: 자리는 JOINED 기준, members 에 내보낸 회원이 없고 재참여는 여전히 403")
    void kick_thenRedisLoss_recoveryExcludesKicked() throws Exception {
        long partyId = createPartyAs(host, customBody(4, "OFFLINE"));
        joinAs(guest, partyId);
        joinAs(other, partyId);
        kick(host, partyId, guest).andExpect(status().isOk());
        redisTemplate.delete(redisTemplate.keys("party:*")); // Redis 재시작 시뮬레이션
        Member third = saveMember("third");

        // 복구: 남은 자리 = 4 - JOINED 2(host, other) = 2 → third 가 들어오면 1
        joinRequest(third, partyId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remaining").value(1));

        assertThat(redisTemplate.opsForSet().members(membersKey(partyId)))
                .containsExactlyInAnyOrder(
                        String.valueOf(host.getId()), String.valueOf(other.getId()), String.valueOf(third.getId()));
        joinRequest(guest, partyId).andExpect(status().isForbidden());
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(3);
    }
}
