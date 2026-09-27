package com.boardgame.reservation.reservation.event;

/**
 * 신청자 본인이 예약을 취소했을 때 발행 (알림 대상: 게임 소유 관리자).
 * 게임 운영 중지로 자동 취소된 예약에는 발행하지 않는다 — 그쪽은 BoardGameSuspendedEvent.cancelledReservationIds.
 */
public record ReservationCancelledEvent(Long reservationId) {
}
