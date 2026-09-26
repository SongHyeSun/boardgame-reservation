package com.boardgame.reservation.member.event;

/** 관리자 권한을 신청했을 때(가입 시 신청 포함, 재신청 포함) 발행. 엔티티가 아니라 id 만 담는다. 리스너는 알림 단계(D)에서 추가. */
public record AdminRequestedEvent(Long memberId) {
}
