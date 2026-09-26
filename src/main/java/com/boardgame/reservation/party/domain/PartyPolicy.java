package com.boardgame.reservation.party.domain;

/** 파티 규칙 상수. 기타 게임 파티는 게임별 인원 범위가 없어 고정 범위(호스트 포함)를 쓴다 */
public final class PartyPolicy {

    public static final int CUSTOM_GAME_MIN_CAPACITY = 2;
    public static final int CUSTOM_GAME_MAX_CAPACITY = 20;

    private PartyPolicy() {
    }
}
