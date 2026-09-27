package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationOccupancy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationOccupancyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final BoardGame GAME = BoardGame.create("Catan", 3, 4, 60, Difficulty.NORMAL, "설명");
    private static final Member MEMBER = Member.createUser("a@test.com", "pw", "a");

    private static Reservation reservation(int startOffset, int endOffset) {
        return Reservation.create(GAME, MEMBER, TODAY.plusDays(startOffset), TODAY.plusDays(endOffset));
    }

    @Test
    @DisplayName("날짜별 점유: 부분적으로 겹치는 예약들의 날짜별 값이 정확하고, 예약 없는 날짜는 0 이다")
    void countByDate_partialOverlaps() {
        // A: +1~+3, B: +2~+4, C: +4~+4  / 조회 범위 +0~+5
        List<Reservation> active = List.of(reservation(1, 3), reservation(2, 4), reservation(4, 4));

        Map<LocalDate, Integer> counts = ReservationOccupancy.countByDate(active, TODAY, TODAY.plusDays(5));

        assertThat(counts).containsExactly(
                Map.entry(TODAY, 0),
                Map.entry(TODAY.plusDays(1), 1),
                Map.entry(TODAY.plusDays(2), 2),
                Map.entry(TODAY.plusDays(3), 2),
                Map.entry(TODAY.plusDays(4), 2),
                Map.entry(TODAY.plusDays(5), 0));
    }

    @Test
    @DisplayName("날짜별 점유: 재고 2 에서 가용 수량(stock - 점유)이 날짜별로 2,1,0,0,0,2 로 나온다")
    void availability_stock2() {
        List<Reservation> active = List.of(reservation(1, 3), reservation(2, 4), reservation(4, 4));
        int stock = 2;

        List<Integer> available = ReservationOccupancy.countByDate(active, TODAY, TODAY.plusDays(5))
                .values().stream().map(count -> stock - count).toList();

        assertThat(available).containsExactly(2, 1, 0, 0, 0, 2);
    }

    @Test
    @DisplayName("날짜별 점유: 조회 범위 밖으로 걸친 예약은 범위 안 날짜만 센다")
    void countByDate_clipsToRange() {
        List<Reservation> active = List.of(reservation(-2, 1)); // 오늘-2 ~ 오늘+1

        Map<LocalDate, Integer> counts = ReservationOccupancy.countByDate(active, TODAY, TODAY.plusDays(2));

        assertThat(counts).containsExactly(
                Map.entry(TODAY, 1),
                Map.entry(TODAY.plusDays(1), 1),
                Map.entry(TODAY.plusDays(2), 0));
    }

    @Test
    @DisplayName("날짜별 점유: 예약이 없으면 범위 전체가 0")
    void countByDate_empty() {
        assertThat(ReservationOccupancy.countByDate(List.of(), TODAY, TODAY.plusDays(2)).values())
                .containsExactly(0, 0, 0);
    }

    @Test
    @DisplayName("최대 점유(재고 줄이기 검사): 오늘 이전 부분은 잘라내고, 오늘 이후 가장 많이 겹치는 날의 수를 돌려준다")
    void maxOccupiedFrom_clipsPast() {
        // R1: 오늘-5 ~ 오늘+1, R2: 오늘+1 ~ 오늘+2, R3: 오늘-5 ~ 오늘-1(이미 끝남)
        List<Reservation> active = List.of(reservation(-5, 1), reservation(1, 2), reservation(-5, -1));

        assertThat(ReservationOccupancy.maxOccupiedFrom(active, TODAY)).isEqualTo(2); // 오늘+1 에 R1·R2
    }

    @Test
    @DisplayName("최대 점유: 과거에만 겹친 예약은 세지 않는다")
    void maxOccupiedFrom_pastOverlapIgnored() {
        List<Reservation> active = List.of(reservation(-5, -2), reservation(-4, -1), reservation(0, 0));

        assertThat(ReservationOccupancy.maxOccupiedFrom(active, TODAY)).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 점유: 예약이 없으면 0")
    void maxOccupiedFrom_empty() {
        assertThat(ReservationOccupancy.maxOccupiedFrom(List.of(), TODAY)).isZero();
    }
}
