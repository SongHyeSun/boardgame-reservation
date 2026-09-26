package com.boardgame.reservation.party.dto;

import com.boardgame.reservation.boardgame.domain.PlayMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * capacity 는 호스트 포함 인원. 게임은 boardGameId(등록된 보드게임) 와 customGameName(기타 게임) 중 정확히 하나,
 * 인원 범위·방식 지원 여부·접속 링크 형식 같은 규칙 검증은 서비스/도메인에서 한다.
 */
public record PartyCreateRequest(

        Long boardGameId,

        @Size(max = 50, message = "게임 이름은 50자 이하여야 합니다.")
        String customGameName,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        String title,

        @Size(max = 2000, message = "설명은 2000자 이하여야 합니다.")
        String description,

        @NotNull(message = "모집 인원은 필수입니다.")
        @Min(value = 1, message = "모집 인원은 1명 이상이어야 합니다.")
        Integer capacity,

        LocalDateTime playAt,

        @NotNull(message = "진행 방식은 필수입니다.")
        PlayMode playMode,

        @Size(max = 30, message = "플랫폼은 30자 이하여야 합니다.")
        String onlinePlatform,

        @Size(max = 300, message = "접속 링크는 300자 이하여야 합니다.")
        String onlineLink,

        @Size(max = 100, message = "장소는 100자 이하여야 합니다.")
        String location
) {
}
