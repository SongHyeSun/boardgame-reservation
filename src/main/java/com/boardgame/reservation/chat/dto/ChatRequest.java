package com.boardgame.reservation.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * POST /api/chat/recommend. history 최대 개수는 app.chat.max-history(기본 10)와 같은 값을
 * 코드에 하드코딩했다(bean validation은 설정값을 동적으로 읽을 수 없어서 나온 의도적 단순화).
 */
public record ChatRequest(
        @NotBlank(message = "message 는 필수입니다.")
        @Size(max = 500, message = "message 는 500자 이하여야 합니다.")
        String message,

        @Valid
        @Size(max = 10, message = "history 는 최대 10개까지 가능합니다.")
        List<ChatHistoryItem> history
) {
    public ChatRequest {
        if (history == null) {
            history = List.of();
        }
    }
}
