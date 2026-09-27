package com.boardgame.reservation.notification.domain;

/** notification-plan.md 6장 알림 종류 표 그대로. */
public enum NotificationType {
    PARTY_JOINED,
    PARTY_FULL,
    PARTY_LEFT,
    PARTY_KICKED,
    PARTY_CLOSED,
    GAME_SUSPENDED,
    RESERVATION_REQUESTED,
    RESERVATION_APPROVED,
    RESERVATION_REJECTED,
    RESERVATION_CANCELLED,
    ADMIN_REQUESTED,
    ADMIN_APPROVED,
    ADMIN_REJECTED
}
