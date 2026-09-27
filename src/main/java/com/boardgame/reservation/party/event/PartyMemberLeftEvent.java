package com.boardgame.reservation.party.event;

/** 참여자가 자진 탈퇴했을 때 발행 (알림 대상: 호스트). 엔티티가 아니라 id 만 담는다. */
public record PartyMemberLeftEvent(Long partyId, Long memberId) {
}
