package com.boardgame.reservation.reservation.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ReservationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    /** 재고를 점유하는 상태: 승인 전(PENDING)도 그 날짜의 재고를 잡고 있다 */
    public static final Set<ReservationStatus> ACTIVE = EnumSet.of(PENDING, APPROVED);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
