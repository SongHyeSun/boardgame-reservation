package com.boardgame.reservation.boardgame.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/boardgames/{id}/visibility — false 면 운영 중지(숨기기), true 면 다시 보이기 */
public record BoardGameVisibilityRequest(

        @NotNull(message = "visible 은 필수입니다.")
        Boolean visible
) {
}
