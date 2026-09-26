package com.boardgame.reservation.member.event;

/** SUPER_ADMIN 이 관리자 신청을 승인(approved=true) 또는 거절(false)했을 때 발행. memberId 는 신청자. */
public record AdminRequestDecidedEvent(Long memberId, boolean approved) {
}
