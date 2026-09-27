package com.boardgame.reservation.reservation.event;

/** 소유 관리자가 승인(approved=true)·거절(false)했을 때 발행 (알림 대상: 신청자). 거절 사유는 예약의 rejectReason 에서 읽는다. */
public record ReservationDecidedEvent(Long reservationId, boolean approved) {
}
