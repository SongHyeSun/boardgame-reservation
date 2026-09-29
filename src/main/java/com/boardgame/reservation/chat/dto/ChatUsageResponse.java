package com.boardgame.reservation.chat.dto;

/** GET /api/chat/usage 응답 */
public record ChatUsageResponse(long limit, long used, long remaining) {
}
