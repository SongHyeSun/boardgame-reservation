package com.boardgame.reservation.boardgame.dto;

import com.boardgame.reservation.boardgame.domain.Difficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 등록(POST) / 수정(PUT) 공용 요청. minPlayers <= maxPlayers 는 서비스에서 검증한다. */
public record BoardGameRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        String name,

        @NotNull(message = "최소 인원은 필수입니다.")
        @Min(value = 1, message = "최소 인원은 1명 이상이어야 합니다.")
        Integer minPlayers,

        @NotNull(message = "최대 인원은 필수입니다.")
        @Min(value = 1, message = "최대 인원은 1명 이상이어야 합니다.")
        Integer maxPlayers,

        @NotNull(message = "플레이 시간은 필수입니다.")
        @Min(value = 1, message = "플레이 시간은 1분 이상이어야 합니다.")
        Integer playTime,

        @NotNull(message = "난이도는 필수입니다.")
        Difficulty difficulty,

        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.")
        String description
) {
}
