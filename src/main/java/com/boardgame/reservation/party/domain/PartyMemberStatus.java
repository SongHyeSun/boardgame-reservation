package com.boardgame.reservation.party.domain;

/** JOINED: 참여 중. KICKED: 호스트가 내보냄 — 행을 남겨 UNIQUE(party_id, member_id) 로 재참여를 막는다 */
public enum PartyMemberStatus {
    JOINED,
    KICKED
}
