package com.boardgame.reservation.chat.dto;

import java.util.List;

/** POST /api/chat/recommend 응답. 웹 검색은 범위 밖이라 sources 없음 */
public record ChatResponse(String answer, List<ChatRecommendationResponse> recommendations, long remainingToday) {
}
