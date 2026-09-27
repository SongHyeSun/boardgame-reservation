package com.boardgame.reservation.reservation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** POST /api/reservations. 당일 대여는 startDate = endDate. 기간 정책(60일·7일)은 서비스가 검증한다 */
public record ReservationCreateRequest(

        @NotNull(message = "boardGameId 는 필수입니다.")
        Long boardGameId,

        @NotNull(message = "startDate 는 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "endDate 는 필수입니다.")
        LocalDate endDate
) {
}
