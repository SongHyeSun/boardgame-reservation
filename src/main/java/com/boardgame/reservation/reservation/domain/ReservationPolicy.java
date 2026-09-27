package com.boardgame.reservation.reservation.domain;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 예약 정책 상수와 기간 검증. 날짜 기준(today)은 호출하는 쪽이 Asia/Seoul Clock 으로 만들어 넘긴다. */
public final class ReservationPolicy {

    /** 시작일은 오늘 ~ 오늘 + 60일 */
    public static final int MAX_ADVANCE_DAYS = 60;
    /** 대여 기간은 최대 7일 (당일 = 1일) */
    public static final int MAX_DURATION_DAYS = 7;
    /** 달력 조회 범위는 from~to 양끝 포함 최대 62일 */
    public static final int MAX_AVAILABILITY_RANGE_DAYS = 62;
    /**
     * 게임·예약 행 비관적 락 대기 한도(ms). 어노테이션 힌트 값이라 문자열 상수.
     * PostgreSQL 에서는 Hibernate 가 잠금 SELECT 직전에 `set local lock_timeout` 으로 적용한다.
     */
    public static final String LOCK_TIMEOUT_MILLIS = "3000";

    private ReservationPolicy() {
    }

    /** 양끝을 포함한 일수 (당일 = 1) */
    public static long durationDays(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** 과거 시작·start > end·시작이 60일 초과·기간이 7일 초과 → INVALID_RESERVATION_PERIOD(400) */
    public static void validatePeriod(LocalDate start, LocalDate end, LocalDate today) {
        if (start == null || end == null
                || start.isBefore(today)
                || end.isBefore(start)
                || start.isAfter(today.plusDays(MAX_ADVANCE_DAYS))
                || durationDays(start, end) > MAX_DURATION_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_PERIOD,
                    "예약은 오늘부터 %d일 이내에 시작하는 최대 %d일까지 신청할 수 있습니다."
                            .formatted(MAX_ADVANCE_DAYS, MAX_DURATION_DAYS));
        }
    }

    /** 달력 조회 범위: from > to 이거나 62일 초과 → INVALID_INPUT(400). 과거 from 은 허용(달력 첫 주) */
    public static void validateAvailabilityRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || durationDays(from, to) > MAX_AVAILABILITY_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "조회 범위는 시작일부터 종료일까지 최대 %d일이며, 시작일이 종료일보다 늦을 수 없습니다."
                            .formatted(MAX_AVAILABILITY_RANGE_DAYS));
        }
    }
}
