package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.event.ReservationCancelledEvent;
import com.boardgame.reservation.reservation.event.ReservationDecidedEvent;
import com.boardgame.reservation.reservation.event.ReservationRequestedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약 API 통합 테스트: 신청 · 달력 가용 조회 · 내 예약 · 취소 · 관리자 승인/거절 (Security 필터 체인 + H2).
 * 게임 수정·숨기기와의 연동은 ReservationGameLinkIntegrationTest.
 */
@RecordApplicationEvents
class ReservationApiIntegrationTest extends ReservationApiTestSupport {

    @Autowired
    ApplicationEvents applicationEvents;

    // ───────────── 신청 ─────────────

    @Test
    @DisplayName("신청 201: PENDING 으로 저장되고 게임 정보·기간이 응답에 담기며 ReservationRequestedEvent 가 발행된다")
    void create_success() throws Exception {
        reserve(guest, game, 1, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.boardGameId").value(game.getId()))
                .andExpect(jsonPath("$.data.boardGameName").value("Catan"))
                .andExpect(jsonPath("$.data.boardGameVisible").value(true))
                .andExpect(jsonPath("$.data.startDate").value(day(1).toString()))
                .andExpect(jsonPath("$.data.endDate").value(day(2).toString()))
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()))
                .andExpect(jsonPath("$.data.rejectReason").value(nullValue()));

        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(applicationEvents.stream(ReservationRequestedEvent.class).toList()).hasSize(1);
    }

    @Test
    @DisplayName("당일 대여(start = end = 오늘)와 오늘+60일 시작·7일 기간 경계는 신청할 수 있다")
    void create_boundaryPeriods() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 5);
        reserve(guest, roomy, 0, 0).andExpect(status().isCreated());
        reserve(guest, roomy, 60, 60).andExpect(status().isCreated());
        reserve(guest, roomy, 10, 16).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("비로그인은 신청·내 예약·취소 모두 401")
    void unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(game.getId(), day(1), day(1))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reservations/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/reservations/{id}/cancel", 1L)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("기간 규칙 위반은 400 (과거 시작·역순·60일 초과·7일 초과), 메시지에 규칙이 담긴다")
    void create_invalidPeriod_400() throws Exception {
        reserve(guest, game, -1, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("60일")))
                .andExpect(jsonPath("$.message").value(containsString("7일")));
        reserve(guest, game, 3, 2).andExpect(status().isBadRequest());
        reserve(guest, game, 61, 61).andExpect(status().isBadRequest());
        reserve(guest, game, 0, 7).andExpect(status().isBadRequest());

        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    @DisplayName("필수 값 누락·날짜 형식 오류는 400")
    void create_invalidBody_400() throws Exception {
        mockMvc.perform(post("/api/reservations").with(loginAs(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardGameId\": %d, \"startDate\": \"%s\"}".formatted(game.getId(), day(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("endDate")));
        mockMvc.perform(post("/api/reservations").with(loginAs(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(game.getId(), "내일", "모레")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 게임 404, 숨김 게임 409, 온라인 전용 게임 409(RESERVATION_NOT_SUPPORTED 메시지)")
    void create_gameStates() throws Exception {
        mockMvc.perform(post("/api/reservations").with(loginAs(guest))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(999_999L, day(1), day(1))))
                .andExpect(status().isNotFound());

        game.hide();
        boardGameRepository.save(game);
        reserve(guest, game, 1, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.BOARDGAME_NOT_AVAILABLE.getMessage()));

        BoardGame online = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Codenames", 2, 8, 20, Difficulty.EASY, "온라인", false, true, 0), owner));
        reserve(guest, online, 1, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("온라인 전용 게임은 대여할 수 없습니다."));
    }

    @Test
    @DisplayName("재고 1: 승인 전(PENDING)에도 점유되어 같은 날짜 신청은 409 NOT_AVAILABLE, 겹치지 않는 날짜는 가능")
    void create_pendingOccupiesStock() throws Exception {
        reserveOk(guest, game, 1, 2);

        reserve(other, game, 2, 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("선택한 기간에 대여 가능한 재고가 없습니다."));
        reserve(other, game, 3, 4).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("같은 회원이 같은 게임에 기간이 겹치는 활성 예약을 또 신청하면 409 DUPLICATE_RESERVATION")
    void create_duplicate() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 5);
        reserveOk(guest, roomy, 1, 3);

        reserve(guest, roomy, 3, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 해당 기간에 예약한 게임입니다."));
        reserve(guest, roomy, 4, 5).andExpect(status().isCreated()); // 겹치지 않으면 가능
    }

    @Test
    @DisplayName("재고 2: 두 건까지 같은 날짜에 신청되고 세 번째는 거절된다")
    void create_stock2() throws Exception {
        BoardGame two = saveOfflineGame(owner, 2);
        reserveOk(guest, two, 1, 1);
        reserveOk(other, two, 1, 1);

        reserve(saveMember("third"), two, 1, 1).andExpect(status().isConflict());
    }

    // ───────────── 달력 가용 조회 ─────────────

    @Test
    @DisplayName("availability 는 비로그인도 200: 날짜별 남은 수량이 from~to 양끝 포함으로 내려오고 신청·거절·취소에 따라 바뀐다")
    void availability_public_reflectsReservations() throws Exception {
        BoardGame two = saveOfflineGame(owner, 2);
        String url = "/api/boardgames/{id}/availability?from={from}&to={to}";

        mockMvc.perform(get(url, two.getId(), day(0), day(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].date").value(day(0).toString()))
                .andExpect(jsonPath("$.data[*].available", contains(2, 2, 2, 2)));

        long first = reserveOk(guest, two, 1, 2);
        reserveOk(other, two, 2, 3);
        mockMvc.perform(get(url, two.getId(), day(0), day(3)))
                .andExpect(jsonPath("$.data[*].available", contains(2, 1, 0, 1)));

        approve(owner, first).andExpect(status().isOk()); // 승인 후에도 점유 유지
        mockMvc.perform(get(url, two.getId(), day(0), day(3)))
                .andExpect(jsonPath("$.data[*].available", contains(2, 1, 0, 1)));

        cancel(guest, first).andExpect(status().isOk()); // 취소되면 점유 해제
        mockMvc.perform(get(url, two.getId(), day(0), day(3)))
                .andExpect(jsonPath("$.data[*].available", contains(2, 2, 1, 1)));
    }

    @Test
    @DisplayName("거절되면 재고 점유가 풀려 달력에서 다시 선택할 수 있다")
    void availability_rejectFreesStock() throws Exception {
        long id = reserveOk(guest, game, 1, 1);
        String url = "/api/boardgames/{id}/availability?from={from}&to={to}";
        mockMvc.perform(get(url, game.getId(), day(1), day(1))).andExpect(jsonPath("$.data[0].available").value(0));

        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", id).with(loginAs(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get(url, game.getId(), day(1), day(1))).andExpect(jsonPath("$.data[0].available").value(1));
    }

    @Test
    @DisplayName("availability 범위: 62일 OK, 63일·역순·파라미터 누락·형식 오류는 400")
    void availability_rangeValidation() throws Exception {
        String url = "/api/boardgames/{id}/availability";

        mockMvc.perform(get(url, game.getId()).param("from", day(0).toString()).param("to", day(61).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(62)));
        mockMvc.perform(get(url, game.getId()).param("from", day(0).toString()).param("to", day(62).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url, game.getId()).param("from", day(2).toString()).param("to", day(1).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url, game.getId()).param("from", day(0).toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(url, game.getId()).param("from", "abc").param("to", day(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("availability: 과거 from 허용, 없는 게임 404, 숨김 게임·온라인 전용 게임 409")
    void availability_gameStates() throws Exception {
        String url = "/api/boardgames/{id}/availability?from={from}&to={to}";
        mockMvc.perform(get(url, game.getId(), day(-3), day(-1))).andExpect(status().isOk());
        mockMvc.perform(get(url, 999_999L, day(0), day(1))).andExpect(status().isNotFound());

        BoardGame online = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Codenames", 2, 8, 20, Difficulty.EASY, "온라인", false, true, 0), owner));
        mockMvc.perform(get(url, online.getId(), day(0), day(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("온라인 전용 게임은 대여할 수 없습니다."));

        game.hide();
        boardGameRepository.save(game);
        mockMvc.perform(get(url, game.getId(), day(0), day(1))).andExpect(status().isConflict());
    }

    // ───────────── 내 예약 ─────────────

    @Test
    @DisplayName("내 예약은 최신순이고 게임명·imageUrl·boardGameVisible·기간·상태·사유가 담기며, 남의 예약은 나오지 않는다")
    void myReservations_newestFirst_onlyMine() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 5);
        long older = reserveOk(guest, roomy, 1, 1);
        long newer = reserveOk(guest, roomy, 5, 6);
        reserveOk(other, roomy, 1, 1);

        mockMvc.perform(get("/api/reservations/me").with(loginAs(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(newer))
                .andExpect(jsonPath("$.data[1].id").value(older))
                .andExpect(jsonPath("$.data[0].boardGameName").value("Catan"))
                .andExpect(jsonPath("$.data[0].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.data[0].boardGameVisible").value(true))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].startDate").value(day(5).toString()))
                .andExpect(jsonPath("$.data[0].endDate").value(day(6).toString()));
    }

    @Test
    @DisplayName("내 예약 status 필터: 그 상태만, 잘못된 값은 400, 없는 상태는 빈 목록")
    void myReservations_statusFilter() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 5);
        long pending = reserveOk(guest, roomy, 1, 1);
        long approved = reserveOk(guest, roomy, 3, 3);
        approve(owner, approved).andExpect(status().isOk());

        mockMvc.perform(get("/api/reservations/me").param("status", "APPROVED").with(loginAs(guest)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(approved));
        mockMvc.perform(get("/api/reservations/me").param("status", "PENDING").with(loginAs(guest)))
                .andExpect(jsonPath("$.data[0].id").value(pending));
        mockMvc.perform(get("/api/reservations/me").param("status", "REJECTED").with(loginAs(guest)))
                .andExpect(jsonPath("$.data", empty()));
        mockMvc.perform(get("/api/reservations/me").param("status", "FOO").with(loginAs(guest)))
                .andExpect(status().isBadRequest());
    }

    // ───────────── 본인 취소 ─────────────

    @Test
    @DisplayName("본인 취소 200: CANCELLED(MEMBER) 로 바뀌고 이벤트가 발행되며, 그 날짜는 다시 신청할 수 있다")
    void cancel_success() throws Exception {
        long id = reserveOk(guest, game, 1, 2);

        cancel(guest, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("MEMBER"));

        assertThat(reload(id).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(applicationEvents.stream(ReservationCancelledEvent.class).toList())
                .singleElement().satisfies(event -> assertThat(event.reservationId()).isEqualTo(id));
        reserve(other, game, 1, 2).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("남의 예약 취소는 404 (존재를 숨김), 없는 예약도 404")
    void cancel_notMine_404() throws Exception {
        long id = reserveOk(guest, game, 1, 2);

        cancel(other, id).andExpect(status().isNotFound());
        cancel(guest, 999_999L).andExpect(status().isNotFound());

        assertThat(reload(id).getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("시작일 당일·이미 시작한 예약, 이미 취소·거절된 예약은 취소할 수 없다 (409 CANNOT_CANCEL_RESERVATION)")
    void cancel_notAllowed_409() throws Exception {
        long startsToday = saveReservation(saveOfflineGame(owner, 2), guest, 0, 1).getId();
        long ongoing = saveReservation(saveOfflineGame(owner, 2), guest, -1, 1).getId();
        long cancelled = reserveOk(guest, game, 3, 3);
        cancel(guest, cancelled).andExpect(status().isOk());

        cancel(guest, startsToday).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("시작일 전날까지")));
        cancel(guest, ongoing).andExpect(status().isConflict());
        cancel(guest, cancelled).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("내일 시작하는 승인된 예약은 오늘 취소할 수 있다 (시작일 전날까지)")
    void cancel_approvedTomorrow_ok() throws Exception {
        long id = reserveOk(guest, game, 1, 1);
        approve(owner, id).andExpect(status().isOk());

        cancel(guest, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    // ───────────── 관리자: 목록 · 승인 · 거절 ─────────────

    @Test
    @DisplayName("관리자 API: 비로그인 401, 일반 회원 403")
    void admin_authorization() throws Exception {
        mockMvc.perform(get("/api/admin/reservations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/reservations").with(loginAs(guest))).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/reservations/{id}/approve", 1L).with(loginAs(guest)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", 1L).with(loginAs(guest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 목록: 내 게임의 예약만, 기본은 PENDING, 신청자 닉네임·이름·아바타 포함, 최신순")
    void admin_list_mineOnly_defaultPending() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 5);
        BoardGame othersGame = saveOfflineGame(otherAdmin, 5);
        long first = reserveOk(guest, roomy, 1, 1);
        long second = reserveOk(other, roomy, 2, 2);
        long approved = reserveOk(other, roomy, 5, 5);
        approve(owner, approved).andExpect(status().isOk());
        reserveOk(guest, othersGame, 1, 1);

        mockMvc.perform(get("/api/admin/reservations").with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(second))
                .andExpect(jsonPath("$.data[1].id").value(first))
                .andExpect(jsonPath("$.data[1].boardGameName").value("Catan"))
                .andExpect(jsonPath("$.data[1].requester.id").value(guest.getId()))
                .andExpect(jsonPath("$.data[1].requester.nickname").value("guest"))
                .andExpect(jsonPath("$.data[1].requester.avatar.type").value("EMOJI"));
        mockMvc.perform(get("/api/admin/reservations").param("status", "APPROVED").with(loginAs(owner)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(approved));
        mockMvc.perform(get("/api/admin/reservations").with(loginAs(otherAdmin)))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("소유 관리자 승인 200: APPROVED, decidedAt 기록, ReservationDecidedEvent(approved=true), 재승인은 409")
    void approve_success() throws Exception {
        long id = reserveOk(guest, game, 1, 2);

        approve(owner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.requester.nickname").value("guest"));

        Reservation reloaded = reload(id);
        assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(reloaded.getDecidedAt()).isNotNull();
        assertThat(applicationEvents.stream(ReservationDecidedEvent.class).toList())
                .singleElement().satisfies(event -> {
                    assertThat(event.reservationId()).isEqualTo(id);
                    assertThat(event.approved()).isTrue();
                });
        approve(owner, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_RESERVATION_STATUS.getMessage()));
    }

    @Test
    @DisplayName("다른 관리자·SUPER_ADMIN 도 남의 게임 예약은 승인·거절할 수 없다 (403 NOT_GAME_OWNER), 상태는 그대로")
    void decide_notOwner_403() throws Exception {
        long id = reserveOk(guest, game, 1, 2);
        Member superAdmin = memberRepository.save(Member.createSuperAdmin("root@test.com", "pw", "root"));

        approve(otherAdmin, id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.NOT_GAME_OWNER.getMessage()));
        approve(superAdmin, id).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", id).with(loginAs(otherAdmin)))
                .andExpect(status().isForbidden());

        assertThat(reload(id).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(applicationEvents.stream(ReservationDecidedEvent.class).toList()).isEmpty();
    }

    @Test
    @DisplayName("거절 200: 사유는 선택(본문 없음·빈 객체 가능), 사유가 있으면 저장되고 내 예약에 노출된다, 이벤트는 approved=false")
    void reject_withAndWithoutReason() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 5);
        long withReason = reserveOk(guest, roomy, 1, 1);
        long withoutBody = reserveOk(guest, roomy, 3, 3);
        long emptyObject = reserveOk(guest, roomy, 5, 5);

        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", withReason).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  재고 점검 중  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("재고 점검 중"));
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", withoutBody).with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rejectReason").value(nullValue()));
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", emptyObject).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reservations/me").param("status", "REJECTED").with(loginAs(guest)))
                .andExpect(jsonPath("$.data", hasSize(3)));
        assertThat(applicationEvents.stream(ReservationDecidedEvent.class).filter(e -> !e.approved()).count()).isEqualTo(3);
        assertThat(reload(withReason).getRejectReason()).isEqualTo("재고 점검 중");
    }

    @Test
    @DisplayName("거절 사유가 100자를 넘으면 400, 이미 처리된 예약 거절·없는 예약은 409·404")
    void reject_validationAndState() throws Exception {
        long id = reserveOk(guest, game, 1, 1);

        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", id).with(loginAs(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"%s\"}".formatted("가".repeat(101))))
                .andExpect(status().isBadRequest());
        approve(owner, id).andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", id).with(loginAs(owner)))
                .andExpect(status().isConflict());
        approve(owner, 999_999L).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미 취소된 예약은 관리자가 승인할 수 없다 (409), 취소된 예약은 관리자 PENDING 목록에서 빠진다")
    void approve_afterMemberCancel_409() throws Exception {
        long id = reserveOk(guest, game, 1, 1);
        cancel(guest, id).andExpect(status().isOk());

        approve(owner, id).andExpect(status().isConflict());
        mockMvc.perform(get("/api/admin/reservations").with(loginAs(owner)))
                .andExpect(jsonPath("$.data", empty()));
        assertThat(reload(id).getCancelReason()).isEqualTo(CancelReason.MEMBER);
    }
}
