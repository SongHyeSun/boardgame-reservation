package com.boardgame.reservation.party.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** capacity 는 호스트 포함 인원. 보드게임 인원 범위 검증은 서비스에서 한다. */
public record PartyCreateRequest(

        @NotNull(message = "보드게임은 필수입니다.")
        Long boardGameId,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.")
        String description,

        @NotNull(message = "모집 인원은 필수입니다.")
        @Min(value = 1, message = "모집 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        LocalDateTime playAt
) {
}
