package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.event.BoardGameSuspendedEvent;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.event.ReservationCancelledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static com.boardgame.reservation.support.MultipartTestUtils.updateBoardGame;
import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예약과 게임 관리(B단계 코드)의 연동을 API 로 검증한다 (H2):
 * 숨기기 시 예약 취소, 재고 줄이기 제한(STOCK_BELOW_RESERVED), 오프라인 끄기 제한(PLAY_MODE_IN_USE).
 * 이 경로들이 게임 행 락을 잡는다는 것(같은 락 공유·경합)은 ReservationLockPostgresTest 에서 실제 PostgreSQL 로 확인한다.
 */
@RecordApplicationEvents
class ReservationGameLinkIntegrationTest extends ReservationApiTestSupport {

    @Autowired
    ApplicationEvents applicationEvents;

    // ───────────── fixture ─────────────

    private ResultActions setVisible(Member actor, BoardGame target, boolean visible) throws Exception {
        return mockMvc.perform(patch("/api/boardgames/{id}/visibility", target.getId()).with(loginAs(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visible\":" + visible + "}"));
    }

    private ResultActions updateGame(Member actor, BoardGame target, boolean offline, boolean online, int stock)
            throws Exception {
        String json = """
                {"name":"Catan","minPlayers":2,"maxPlayers":4,"playTime":60,"difficulty":"NORMAL","description":"설명",
                 "offlineAvailable":%s,"onlineAvailable":%s,"stock":%d}
                """.formatted(offline, online, stock);
        return mockMvc.perform(updateBoardGame(target.getId(), json).with(loginAs(actor)));
    }

    private BoardGame reloadGame(BoardGame target) {
        return boardGameRepository.findById(target.getId()).orElseThrow();
    }

    // ───────────── 숨기기 ─────────────

    @Test
    @DisplayName("⭐ 숨기면 종료일이 오늘 이후인 활성 예약(대기·승인·진행 중)은 GAME_SUSPENDED 로 취소되고, 이미 끝난 예약·거절·본인 취소 건은 그대로다")
    void hide_cancelsUpcomingActiveReservations_keepsHistory() throws Exception {
        BoardGame roomy = saveOfflineGame(owner, 10);
        Member third = saveMember("third");
        long pending = reserveOk(guest, roomy, 3, 4);
        long approvedFuture = reserveOk(other, roomy, 5, 6);
        approve(owner, approvedFuture).andExpect(status().isOk());
        Reservation ongoing = saveReservation(roomy, third, -1, 1); // 어제 시작, 내일 종료 → 아직 안 끝남
        ongoing.approve(LocalDateTime.now(clock));
        reservationRepository.save(ongoing);
        Reservation ended = saveReservation(roomy, guest, -5, -3); // 이미 끝난 예약 = 이력
        ended.approve(LocalDateTime.now(clock).minusDays(6));
        reservationRepository.save(ended);
        long rejected = reserveOk(third, roomy, 8, 8);
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", rejected).with(loginAs(owner)))
                .andExpect(status().isOk());
        long memberCancelled = reserveOk(third, roomy, 10, 10);
        cancel(third, memberCancelled).andExpect(status().isOk());
        long cancelEventsBefore = applicationEvents.stream(ReservationCancelledEvent.class).count();

        setVisible(owner, roomy, false).andExpect(status().isOk()).andExpect(jsonPath("$.data.visible").value(false));

        assertThat(reload(pending).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reload(pending).getCancelReason()).isEqualTo(CancelReason.GAME_SUSPENDED);
        assertThat(reload(approvedFuture).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reload(approvedFuture).getCancelReason()).isEqualTo(CancelReason.GAME_SUSPENDED);
        assertThat(reload(ongoing.getId()).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reload(ongoing.getId()).getCancelReason()).isEqualTo(CancelReason.GAME_SUSPENDED);
        // 이력은 유지
        assertThat(reload(ended.getId()).getStatus()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(reload(ended.getId()).getCancelReason()).isNull();
        assertThat(reload(rejected).getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reload(memberCancelled).getCancelReason()).isEqualTo(CancelReason.MEMBER); // 사유가 덮어써지지 않음

        // 이벤트: 취소된 예약 id 3건, 회원 취소 이벤트는 추가로 발행되지 않는다
        assertThat(applicationEvents.stream(BoardGameSuspendedEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.gameId()).isEqualTo(roomy.getId());
                    assertThat(event.cancelledPartyIds()).isEmpty();
                    assertThat(event.cancelledReservationIds())
                            .containsExactlyInAnyOrder(pending, approvedFuture, ongoing.getId());
                });
        assertThat(applicationEvents.stream(ReservationCancelledEvent.class).count()).isEqualTo(cancelEventsBefore);
    }

    @Test
    @DisplayName("숨긴 뒤 내 예약에는 GAME_SUSPENDED·boardGameVisible=false 로 보이고, 이미 취소된 예약이라 본인이 다시 취소할 수 없다")
    void hide_myReservationsShowSuspended() throws Exception {
        long id = reserveOk(guest, game, 3, 4);

        setVisible(owner, game, false).andExpect(status().isOk());

        mockMvc.perform(get("/api/reservations/me").with(loginAs(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[0].cancelReason").value("GAME_SUSPENDED"))
                .andExpect(jsonPath("$.data[0].boardGameVisible").value(false));
        cancel(guest, id).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("숨긴 게임은 신청·달력 조회 409, 다시 보이면 조회가 되고 취소된 예약은 복구되지 않은 채 그 날짜가 다시 비어 있다")
    void hide_thenShow_datesFreeButReservationsNotRestored() throws Exception {
        long id = reserveOk(guest, game, 3, 4);
        String url = "/api/boardgames/{id}/availability?from={from}&to={to}";

        setVisible(owner, game, false).andExpect(status().isOk());
        reserve(other, game, 3, 4).andExpect(status().isConflict());
        mockMvc.perform(get(url, game.getId(), day(3), day(4))).andExpect(status().isConflict());

        setVisible(owner, game, true).andExpect(status().isOk());
        mockMvc.perform(get(url, game.getId(), day(3), day(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].available").value(1))  // 취소되어 점유 해제 → 재고 1 복구
                .andExpect(jsonPath("$.data[1].available").value(1));
        assertThat(reload(id).getStatus()).isEqualTo(ReservationStatus.CANCELLED); // 복구되지 않는다
        reserve(other, game, 3, 4).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("소유 관리자가 아니면 숨길 수 없고(403) 예약도 그대로다")
    void hide_notOwner_reservationsUntouched() throws Exception {
        long id = reserveOk(guest, game, 3, 4);

        setVisible(otherAdmin, game, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.NOT_GAME_OWNER.getMessage()));

        assertThat(reload(id).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reloadGame(game).isVisible()).isTrue();
    }

    // ───────────── 재고 줄이기 ─────────────

    @Test
    @DisplayName("재고 줄이기: 오늘 이후 최대 점유 수보다 작게는 409 STOCK_BELOW_RESERVED, 같게는 가능 (재고는 바뀌지 않거나 정확히 바뀐다)")
    void reduceStock_belowMaxOccupied_409() throws Exception {
        BoardGame three = saveOfflineGame(owner, 3);
        reserveOk(guest, three, 2, 3);
        reserveOk(other, three, 3, 4); // 3일째에 2건 겹침 → 최대 점유 2
        reserveOk(saveMember("third"), three, 8, 8);

        updateGame(owner, three, true, false, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 예약된 수량보다 재고를 줄일 수 없습니다."));
        assertThat(reloadGame(three).getStock()).isEqualTo(3);

        updateGame(owner, three, true, false, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(2));
        assertThat(reloadGame(three).getStock()).isEqualTo(2);
    }

    @Test
    @DisplayName("재고 늘리기·그대로 두기는 예약이 있어도 가능하고, 늘린 재고만큼 달력에 반영된다")
    void increaseStock_ok() throws Exception {
        BoardGame one = saveOfflineGame(owner, 1);
        reserveOk(guest, one, 2, 2);

        updateGame(owner, one, true, false, 1).andExpect(status().isOk());
        updateGame(owner, one, true, false, 3).andExpect(status().isOk()).andExpect(jsonPath("$.data.stock").value(3));

        mockMvc.perform(get("/api/boardgames/{id}/availability?from={from}&to={to}", one.getId(), day(2), day(2)))
                .andExpect(jsonPath("$.data[0].available").value(2));
    }

    @Test
    @DisplayName("재고 줄이기: 이미 끝난 예약·거절·취소된 예약은 점유로 세지 않는다")
    void reduceStock_ignoresEndedRejectedCancelled() throws Exception {
        BoardGame three = saveOfflineGame(owner, 3);
        for (int i = 0; i < 3; i++) {
            saveReservation(three, saveMember("past" + i), -6, -4); // 이미 끝남 (PENDING 상태로 남아 있어도 endDate < 오늘)
        }
        long rejected = reserveOk(guest, three, 3, 3);
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", rejected).with(loginAs(owner)))
                .andExpect(status().isOk());
        long cancelled = reserveOk(other, three, 3, 3);
        cancel(other, cancelled).andExpect(status().isOk());

        updateGame(owner, three, true, false, 1).andExpect(status().isOk());

        assertThat(reloadGame(three).getStock()).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 줄이기: 진행 중인 예약(어제 시작~내일 종료)은 오늘부터 세어 막는다")
    void reduceStock_ongoingReservationCounts() throws Exception {
        BoardGame two = saveOfflineGame(owner, 2);
        saveReservation(two, guest, -1, 1);
        saveReservation(two, other, 0, 0);

        updateGame(owner, two, true, false, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ErrorCode.STOCK_BELOW_RESERVED.getMessage()));
    }

    // ───────────── 오프라인 끄기 ─────────────

    @Test
    @DisplayName("오프라인 끄기: 남은 활성 예약이 있으면 409 PLAY_MODE_IN_USE(예약 메시지), 게임은 그대로이고 예약도 자동 취소되지 않는다")
    void disableOffline_withUpcomingReservation_409() throws Exception {
        BoardGame both = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Catan", 2, 4, 60, Difficulty.NORMAL, "설명", true, true, 2), owner));
        long id = reserveOk(guest, both, 3, 4);

        updateGame(owner, both, false, true, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("대여 예약")));

        BoardGame unchanged = reloadGame(both);
        assertThat(unchanged.isOfflineAvailable()).isTrue();
        assertThat(unchanged.getStock()).isEqualTo(2);
        assertThat(reload(id).getStatus()).isEqualTo(ReservationStatus.PENDING);

        // 예약이 사라지면(본인 취소) 끌 수 있고 재고는 0 이 된다
        cancel(guest, id).andExpect(status().isOk());
        updateGame(owner, both, false, true, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.offlineAvailable").value(false))
                .andExpect(jsonPath("$.data.stock").value(0));
    }

    @Test
    @DisplayName("오프라인 끄기: 진행 중(오늘 종료)인 예약도 막고, 이미 끝난 예약이나 거절된 예약만 있으면 끌 수 있다")
    void disableOffline_endDateBoundary() throws Exception {
        BoardGame both = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Catan", 2, 4, 60, Difficulty.NORMAL, "설명", true, true, 3), owner));
        Reservation endsToday = saveReservation(both, guest, -2, 0);

        updateGame(owner, both, false, true, 0).andExpect(status().isConflict());

        reservationRepository.delete(endsToday);
        saveReservation(both, guest, -6, -1); // 어제 끝남 → 이력
        long rejected = reserveOk(other, both, 2, 2);
        mockMvc.perform(patch("/api/admin/reservations/{id}/reject", rejected).with(loginAs(owner)))
                .andExpect(status().isOk());

        updateGame(owner, both, false, true, 0).andExpect(status().isOk());
    }

    @Test
    @DisplayName("오프라인 끄기: 소유자가 아니면 예약 검사 전에 403 이고, 두 방식을 모두 끄면 400 이 먼저다")
    void disableOffline_ownerAndValueChecksFirst() throws Exception {
        BoardGame both = boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Catan", 2, 4, 60, Difficulty.NORMAL, "설명", true, true, 2), owner));
        reserveOk(guest, both, 3, 4);

        updateGame(otherAdmin, both, false, true, 0).andExpect(status().isForbidden());
        updateGame(owner, both, false, false, 0).andExpect(status().isBadRequest());

        assertThat(reloadGame(both).isOfflineAvailable()).isTrue();
    }

    @Test
    @DisplayName("예약과 무관한 수정(설명·이름 등)은 예약이 있어도 막히지 않고, 여러 예약의 게임 요약도 새 값으로 보인다")
    void unrelatedUpdate_allowedWithReservations() throws Exception {
        BoardGame one = saveOfflineGame(owner, 1);
        reserveOk(guest, one, 3, 3);
        String json = """
                {"name":"Catan Deluxe","minPlayers":2,"maxPlayers":5,"playTime":90,"difficulty":"HARD","description":"바뀜",
                 "offlineAvailable":true,"onlineAvailable":false,"stock":1}
                """;

        mockMvc.perform(updateBoardGame(one.getId(), json).with(loginAs(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Catan Deluxe"));

        mockMvc.perform(get("/api/reservations/me").with(loginAs(guest)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].boardGameName").value("Catan Deluxe"));
    }
}
