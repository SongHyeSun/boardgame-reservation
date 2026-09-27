package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.dto.ReservationCreateRequest;
import com.boardgame.reservation.reservation.service.ReservationService;
import com.boardgame.reservation.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.boardgame.reservation.support.ConcurrencyTestUtils.count;
import static com.boardgame.reservation.support.ConcurrencyTestUtils.runConcurrently;
import static com.boardgame.reservation.support.ConcurrencyTestUtils.successCount;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ⭐ 재고 동시성 검증 (README 비교 포인트: 파티는 Redis DECR, 예약은 DB 비관적 락).
 * 실제 PostgreSQL(Testcontainers) 에서 서비스를 트랜잭션 없이, 진짜 동시에 호출한다 (각 호출이 독립 커밋).
 * 락이 없으면 모든 스레드가 "가용 1" 을 읽고 INSERT 해서 재고를 넘기므로 이 테스트가 실패한다.
 * 날짜는 실제 Clock 기준 "내일" 이후로 잡아 자정 경계에서도 항상 유효하다.
 */
class ReservationConcurrencyTest extends PostgresIntegrationTestSupport {

    @Autowired
    ReservationService reservationService;
    @Autowired
    Clock clock;

    Member owner;
    LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        owner = saveAdmin("owner");
        tomorrow = LocalDate.now(clock).plusDays(1);
    }

    private Supplier<?> apply(Member member, BoardGame game, int startOffset, int endOffset) {
        ReservationCreateRequest request = new ReservationCreateRequest(
                game.getId(), tomorrow.plusDays(startOffset), tomorrow.plusDays(endOffset));
        return () -> reservationService.create(member.getId(), request);
    }

    private List<Reservation> activeReservations() {
        return reservationRepository.findAll().stream().filter(Reservation::isActive).toList();
    }

    /** 각 날짜별로 그 날짜를 포함하는 활성 예약 수 (서비스 로직과 독립적으로 센다) */
    private static long occupancyOn(List<Reservation> reservations, LocalDate date) {
        return reservations.stream()
                .filter(r -> !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate()))
                .count();
    }

    @Test
    @DisplayName("재고 1 게임에 서로 다른 회원 30명이 같은 날짜로 동시 신청 → 성공 정확히 1, NOT_AVAILABLE 29, DB 예약 1건")
    void stock1_30members_sameDates() throws Exception {
        BoardGame game = saveOfflineGame(owner, 1);
        List<Supplier<?>> tasks = saveMembers("m", 30).stream()
                .<Supplier<?>>map(member -> apply(member, game, 0, 1))
                .toList();

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(count(results, ErrorCode.NOT_AVAILABLE)).isEqualTo(29);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 3 게임에 30명이 같은 날짜로 동시 신청 → 성공 정확히 3, NOT_AVAILABLE 27, DB 예약 3건")
    void stock3_30members_sameDates() throws Exception {
        BoardGame game = saveOfflineGame(owner, 3);
        List<Supplier<?>> tasks = saveMembers("m", 30).stream()
                .<Supplier<?>>map(member -> apply(member, game, 0, 1))
                .toList();

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(3);
        assertThat(count(results, ErrorCode.NOT_AVAILABLE)).isEqualTo(27);
        assertThat(reservationRepository.count()).isEqualTo(3);
    }

    @Test
    @DisplayName("재고 2 게임에 서로 부분적으로 겹치는 기간 40건이 동시 신청 → 어떤 날짜도 재고(2)를 넘지 않는다 (DB 기준)")
    void stock2_40members_partiallyOverlappingPeriods() throws Exception {
        int stock = 2;
        BoardGame game = saveOfflineGame(owner, stock);
        Random random = new Random(20260927L); // 고정 시드: 실패 시 재현 가능
        List<Member> members = saveMembers("m", 40);
        List<Supplier<?>> tasks = new ArrayList<>();
        for (Member member : members) {
            int start = random.nextInt(8);           // 내일 + 0~7
            int length = 1 + random.nextInt(4);      // 1~4일
            tasks.add(apply(member, game, start, start + length - 1));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        // 결과는 성공 아니면 재고 부족뿐 (중복·락 타임아웃 없음)
        assertThat(successCount(results) + count(results, ErrorCode.NOT_AVAILABLE)).isEqualTo(40);
        // 먼저 처리된 1건 뒤에는 어떤 날짜도 점유 1 < 재고 2 라서 두 번째 신청은 반드시 성공한다
        assertThat(successCount(results)).isGreaterThanOrEqualTo(stock);

        List<Reservation> active = activeReservations();
        assertThat(active).hasSize((int) successCount(results));
        for (int offset = 0; offset < 12; offset++) {
            assertThat(occupancyOn(active, tomorrow.plusDays(offset)))
                    .as("점유 수 @ 내일+%d", offset)
                    .isLessThanOrEqualTo(stock);
        }
    }

    @Test
    @DisplayName("재고 3 게임에 겹치지 않는 하루짜리 신청 20건이 동시에 와도 전부 성공한다 (게임 단위로 직렬화될 뿐 거절되지 않음)")
    void stock3_20members_disjointDates_allSucceed() throws Exception {
        BoardGame game = saveOfflineGame(owner, 3);
        List<Member> members = saveMembers("m", 20);
        List<Supplier<?>> tasks = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            tasks.add(apply(members.get(i), game, i, i));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(20);
        assertThat(reservationRepository.count()).isEqualTo(20);
    }

    @Test
    @DisplayName("같은 회원이 같은 기간을 10번 동시 신청 → 성공 1, DUPLICATE_RESERVATION 9, 그 회원의 DB 예약 1건")
    void sameMember_10times_duplicate() throws Exception {
        BoardGame game = saveOfflineGame(owner, 5);
        Member member = saveMember("guest");
        List<Supplier<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(apply(member, game, 0, 2));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(count(results, ErrorCode.DUPLICATE_RESERVATION)).isEqualTo(9);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }
}
