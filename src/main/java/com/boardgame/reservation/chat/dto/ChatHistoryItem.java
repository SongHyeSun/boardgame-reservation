package com.boardgame.reservation.chat.dto;

import com.boardgame.reservation.chat.llm.ChatRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** role이 USER/ASSISTANT가 아니면 Jackson enum 역직렬화 실패로 기존 HttpMessageNotReadableException 핸들러가 400 처리한다 */
public record ChatHistoryItem(
        @NotNull ChatRole role,
        @NotBlank @Size(max = 1000, message = "content 는 1000자 이하여야 합니다.") String content
) {
}
