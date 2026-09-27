package com.boardgame.reservation.reservation;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.reservation.domain.ReservationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private static void assertInvalidPeriod(LocalDate start, LocalDate end) {
        assertThatThrownBy(() -> ReservationPolicy.validatePeriod(start, end, TODAY))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RESERVATION_PERIOD);
    }

    @Test
    @DisplayName("기간 정책: 오늘 당일 대여는 가능하다 (start = end)")
    void period_sameDayToday_ok() {
        assertThatCode(() -> ReservationPolicy.validatePeriod(TODAY, TODAY, TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기간 정책: 시작일이 오늘+60일이면 가능, +61일이면 INVALID_RESERVATION_PERIOD")
    void period_startAdvanceLimit() {
        LocalDate limit = TODAY.plusDays(60);

        assertThatCode(() -> ReservationPolicy.validatePeriod(limit, limit, TODAY)).doesNotThrowAnyException();
        assertInvalidPeriod(limit.plusDays(1), limit.plusDays(1));
    }

    @Test
    @DisplayName("기간 정책: 7일(양끝 포함)까지 가능, 8일이면 INVALID_RESERVATION_PERIOD")
    void period_durationLimit() {
        assertThatCode(() -> ReservationPolicy.validatePeriod(TODAY, TODAY.plusDays(6), TODAY)).doesNotThrowAnyException();
        assertInvalidPeriod(TODAY, TODAY.plusDays(7));
    }

    @Test
    @DisplayName("기간 정책: 과거 시작일은 INVALID_RESERVATION_PERIOD")
    void period_pastStart() {
        assertInvalidPeriod(TODAY.minusDays(1), TODAY);
    }

    @Test
    @DisplayName("기간 정책: 시작일이 종료일보다 늦으면 INVALID_RESERVATION_PERIOD")
    void period_startAfterEnd() {
        assertInvalidPeriod(TODAY.plusDays(3), TODAY.plusDays(2));
    }

    @Test
    @DisplayName("기간 정책: 날짜가 null 이면 INVALID_RESERVATION_PERIOD")
    void period_null() {
        assertInvalidPeriod(null, TODAY);
        assertInvalidPeriod(TODAY, null);
    }

    @Test
    @DisplayName("기간 정책: 종료일은 시작일 기준 7일 규칙만 받는다 (시작 +60일에 시작해 +66일에 끝나도 가능)")
    void period_endMayExceedAdvanceWindow() {
        LocalDate start = TODAY.plusDays(60);

        assertThatCode(() -> ReservationPolicy.validatePeriod(start, start.plusDays(6), TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일수 계산은 양끝을 포함한다 (당일 = 1일)")
    void durationDays_inclusive() {
        assertThat(ReservationPolicy.durationDays(TODAY, TODAY)).isEqualTo(1);
        assertThat(ReservationPolicy.durationDays(TODAY, TODAY.plusDays(6))).isEqualTo(7);
    }

    @Test
    @DisplayName("달력 조회 범위: 양끝 포함 62일까지 가능, 63일이면 INVALID_INPUT")
    void availabilityRange_limit() {
        assertThatCode(() -> ReservationPolicy.validateAvailabilityRange(TODAY, TODAY.plusDays(61)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ReservationPolicy.validateAvailabilityRange(TODAY, TODAY.plusDays(62)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("달력 조회 범위: 역순·null 은 INVALID_INPUT, 과거 from 과 하루짜리 범위는 허용")
    void availabilityRange_reversedAndPast() {
        assertThatThrownBy(() -> ReservationPolicy.validateAvailabilityRange(TODAY.plusDays(1), TODAY))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReservationPolicy.validateAvailabilityRange(null, TODAY))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> ReservationPolicy.validateAvailabilityRange(TODAY.minusDays(10), TODAY.minusDays(3)))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReservationPolicy.validateAvailabilityRange(TODAY, TODAY)).doesNotThrowAnyException();
    }
}
