package com.boardgame.reservation.reservation.event;

/** 대여 신청이 저장됐을 때 발행 (알림 대상: 게임 소유 관리자). 엔티티가 아니라 id 만 담는다. 리스너는 알림 단계(D)에서 추가. */
public record ReservationRequestedEvent(Long reservationId) {
}
