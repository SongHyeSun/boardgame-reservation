package com.boardgame.reservation.party.event;

/** 호스트가 참여자를 내보냈을 때 발행. 엔티티가 아니라 id 만 담는다. 리스너는 알림 단계(D)에서 추가. */
public record PartyMemberKickedEvent(Long partyId, Long memberId) {
}
