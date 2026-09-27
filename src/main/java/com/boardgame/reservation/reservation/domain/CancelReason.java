package com.boardgame.reservation.reservation.domain;

public enum CancelReason {
    /** 신청자 본인이 취소 */
    MEMBER,
    /** 게임이 운영 중지(숨김)되어 자동 취소 */
    GAME_SUSPENDED
}
