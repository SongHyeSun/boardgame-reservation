package com.boardgame.reservation.party.event;

/**
 * 참여가 성공했을 때 발행 (알림 대상: 호스트). 엔티티가 아니라 id 만 담는다.
 * remaining 은 참여 직후 남은 자리 수 — 0 이면 리스너가 PARTY_FULL 알림도 함께 만든다.
 */
public record PartyMemberJoinedEvent(Long partyId, Long memberId, long remaining) {
}
