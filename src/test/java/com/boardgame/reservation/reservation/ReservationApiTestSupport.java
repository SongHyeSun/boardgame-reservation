package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDate;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약 API 통합 테스트(Security 필터 체인 + H2 + Redis 컨테이너 공유)의 공통 부모.
 * 락 동작·동시성은 PostgreSQL 이 필요해서 여기가 아니라 ReservationConcurrencyTest/ReservationLockPostgresTest 에서 다룬다.
 *
 * 준비: 게임 소유 관리자(owner), 다른 관리자(otherAdmin), 일반 회원 2명(guest/other), 오프라인 전용·재고 1 게임(game).
 * 날짜는 실제 Clock(Asia/Seoul) 기준 오늘 + offset 으로 만든다.
 */
abstract class ReservationApiTestSupport extends RedisIntegrationTestSupport {

    @Autowired
    protected WebApplicationContext context;
    @Autowired
    protected Clock clock;

    protected MockMvc mockMvc;
    protected Member owner;
    protected Member otherAdmin;
    protected Member guest;
    protected Member other;
    protected BoardGame game;

    @BeforeEach
    void setUpReservationApi() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        owner = saveAdmin("owner");
        otherAdmin = saveAdmin("other-admin");
        guest = saveMember("guest");
        other = saveMember("other");
        game = saveOfflineGame(owner, 1);
    }

    protected LocalDate day(int offsetFromToday) {
        return LocalDate.now(clock).plusDays(offsetFromToday);
    }

    protected static String reservationJson(Long boardGameId, Object startDate, Object endDate) {
        return """
                {"boardGameId": %s, "startDate": "%s", "endDate": "%s"}
                """.formatted(boardGameId, startDate, endDate);
    }

    /** POST /api/reservations (결과 검증은 호출하는 쪽에서) */
    protected ResultActions reserve(Member member, BoardGame target, int startOffset, int endOffset) throws Exception {
        return mockMvc.perform(post("/api/reservations")
                .with(loginAs(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson(target.getId(), day(startOffset), day(endOffset))));
    }

    /** 신청이 201 로 성공하길 기대하고 생성된 예약 id 를 돌려준다 */
    protected long reserveOk(Member member, BoardGame target, int startOffset, int endOffset) throws Exception {
        String response = reserve(member, target, startOffset, endOffset)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    /** 기간 정책을 거치지 않고 저장 (이미 끝났거나 진행 중인 예약처럼 API 로 만들 수 없는 상태용) */
    protected Reservation saveReservation(BoardGame target, Member member, int startOffset, int endOffset) {
        return reservationRepository.save(Reservation.create(target, member, day(startOffset), day(endOffset)));
    }

    protected ResultActions cancel(Member member, long reservationId) throws Exception {
        return mockMvc.perform(patch("/api/reservations/{id}/cancel", reservationId).with(loginAs(member)));
    }

    protected ResultActions approve(Member admin, long reservationId) throws Exception {
        return mockMvc.perform(patch("/api/admin/reservations/{id}/approve", reservationId).with(loginAs(admin)));
    }

    protected Reservation reload(long reservationId) {
        return reservationRepository.findById(reservationId).orElseThrow();
    }
}
