package com.boardgame.reservation.reservation.dto;

import java.time.LocalDate;

/** 날짜별 남은 수량 (stock - 그 날짜를 포함하는 활성 예약 수, 0 미만은 0). 0 이면 화면에서 "예약 마감" */
public record AvailabilityResponse(LocalDate date, int available) {
}
