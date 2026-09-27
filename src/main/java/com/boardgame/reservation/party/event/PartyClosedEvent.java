package com.boardgame.reservation.party.event;

/** 호스트가 파티를 마감했을 때 발행 (알림 대상: 호스트를 제외한 참여자 전원). 엔티티가 아니라 id 만 담는다. */
public record PartyClosedEvent(Long partyId) {
}
