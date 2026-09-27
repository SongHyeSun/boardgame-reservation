package com.boardgame.reservation.reservation.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 날짜별 점유 수 계산 (순수 로직). 넘기는 예약은 활성(PENDING·APPROVED)만이라고 가정한다.
 * 게임 행 락 안에서 읽은 예약 목록으로 계산하므로 범위(최대 7~62일)가 작아 DB 집계 대신 자바로 센다.
 * 날짜별 가용 수량 = stock - 점유 수.
 */
public final class ReservationOccupancy {

    private ReservationOccupancy() {
    }

    /** [from, to] 의 모든 날짜(0 포함)에 대해, 그 날짜를 포함하는 예약 수 */
    public static Map<LocalDate, Integer> countByDate(Collection<Reservation> reservations, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> counts = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            counts.put(date, 0);
        }
        for (Reservation reservation : reservations) {
            LocalDate first = max(reservation.getStartDate(), from);
            LocalDate last = min(reservation.getEndDate(), to);
            for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
                counts.merge(date, 1, Integer::sum);
            }
        }
        return counts;
    }

    /** from(오늘) 이후 날짜들 중 가장 많이 점유된 날의 점유 수. 각 예약은 from 이전 부분을 잘라서 센다 (재고 줄이기 검사용) */
    public static int maxOccupiedFrom(Collection<Reservation> reservations, LocalDate from) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        int max = 0;
        for (Reservation reservation : reservations) {
            for (LocalDate date = max(reservation.getStartDate(), from);
                 !date.isAfter(reservation.getEndDate());
                 date = date.plusDays(1)) {
                max = Math.max(max, counts.merge(date, 1, Integer::sum));
            }
        }
        return max;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
