package com.boardgame.reservation.boardgame.dto;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 등록(POST) / 수정(PUT) 공용 요청 — multipart 의 data 파트.
 * minPlayers <= maxPlayers, 진행 방식·재고 규칙, 유튜브 링크 형식은 서비스/도메인에서 검증한다.
 * 선택 boolean 은 원시 boolean 이 아니라 Boolean (Jackson 3 는 생략 시 null 매핑 오류).
 */
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
        String description,

        @NotNull(message = "오프라인 가능 여부는 필수입니다.")
        Boolean offlineAvailable,

        @NotNull(message = "온라인 가능 여부는 필수입니다.")
        Boolean onlineAvailable,

        /** 오프라인 가능이면 1 이상. 온라인 전용이면 무시하고 0 으로 저장된다 */
        @NotNull(message = "재고는 필수입니다.")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer stock,

        /** 비어 있으면 영상 없음 (PUT 은 전체 교체라 생략하면 기존 영상이 제거된다) */
        @Size(max = 200, message = "유튜브 링크는 200자 이하여야 합니다.")
        String youtubeUrl,

        /** PUT 전용: true 면 현재 이미지를 제거. 새 image 파트와 함께 보낼 수 없다. POST 에서는 무시 */
        Boolean removeImage
) {

    public boolean imageRemoved() {
        return Boolean.TRUE.equals(removeImage);
    }

    public BoardGame.Details toDetails() {
        return new BoardGame.Details(
                name.trim(), minPlayers, maxPlayers, playTime, difficulty, description,
                offlineAvailable, onlineAvailable, stock);
    }
}
