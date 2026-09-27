package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.service.BoardGameService;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.dto.AdminReservationResponse;
import com.boardgame.reservation.reservation.dto.AvailabilityResponse;
import com.boardgame.reservation.reservation.dto.ReservationCreateRequest;
import com.boardgame.reservation.reservation.dto.ReservationResponse;
import com.boardgame.reservation.reservation.service.ReservationService;
import com.boardgame.reservation.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.boardgame.reservation.support.ConcurrencyTestUtils.count;
import static com.boardgame.reservation.support.ConcurrencyTestUtils.runConcurrently;
import static com.boardgame.reservation.support.ConcurrencyTestUtils.successCount;
import static com.boardgame.reservation.support.SecurityTestUtils.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 PostgreSQL(Testcontainers)에서만 확인할 수 있는 락 동작:
 * - 락 대기 타임아웃 힌트(3초)가 적용되고 API 에서 409 RESERVATION_BUSY 로 나가는지
 * - read-only 트랜잭션에서 FOR UPDATE 가 거부되는지 (H2 는 통과 → 락 메서드는 쓰기 트랜잭션에서만)
 * - 신청·재고 수정·숨기기가 정말 같은 게임 행 락을 공유하는지, 섞여서 동시에 와도 불변식이 지켜지는지
 * - 서비스 전체 흐름이 PG 에서 트랜잭션 오류 없이 도는지
 */
class ReservationLockPostgresTest extends PostgresIntegrationTestSupport {

    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    ReservationService reservationService;
    @Autowired
    BoardGameService boardGameService;
    @Autowired
    WebApplicationContext context;
    @Autowired
    Clock clock;

    TransactionTemplate tx;
    LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tomorrow = LocalDate.now(clock).plusDays(1); // 자정을 넘겨도 항상 유효한 날짜
    }

    private BoardGame saveGame() {
        return boardGameRepository.save(BoardGame.create("Catan", 3, 4, 60, Difficulty.NORMAL, "설명"));
    }

    /** 별도 트랜잭션에서 게임 행 락을 잡고, close()(또는 maxHoldMillis 경과) 때 커밋해서 푼다 */
    private final class LockHolder implements AutoCloseable {
        private final ExecutorService pool = Executors.newSingleThreadExecutor();
        private final CountDownLatch release = new CountDownLatch(1);
        private final Future<?> holder;

        LockHolder(Long gameId, long maxHoldMillis) throws InterruptedException {
            CountDownLatch locked = new CountDownLatch(1);
            holder = pool.submit(() -> tx.executeWithoutResult(status -> {
                boardGameRepository.findByIdForUpdate(gameId);
                locked.countDown();
                try {
                    release.await(maxHoldMillis, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            pool.shutdownNow();
        }
    }

    private static long elapsedMillis(Runnable call) {
        long startedAt = System.nanoTime();
        call.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private ReservationCreateRequest reservationRequest(BoardGame game, int startOffset, int endOffset) {
        return new ReservationCreateRequest(game.getId(), tomorrow.plusDays(startOffset), tomorrow.plusDays(endOffset));
    }

    private static BoardGameRequest gameRequest(int stock) {
        return new BoardGameRequest("Catan", 3, 4, 60, Difficulty.NORMAL, "설명", true, false, stock, null, null);
    }

    private static long occupancyOn(List<Reservation> reservations, LocalDate date) {
        return reservations.stream()
                .filter(Reservation::isActive)
                .filter(r -> !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate()))
                .count();
    }

    // ───────────── 락 타임아웃 · read-only ─────────────

    @Test
    @DisplayName("게임 행 락을 다른 트랜잭션이 잡고 있으면 약 3초 뒤 PessimisticLockingFailureException (PostgreSQL lock_timeout 적용)")
    void lockTimeout_after3seconds() throws Exception {
        BoardGame game = saveGame();
        try (LockHolder ignored = new LockHolder(game.getId(), 30_000)) {
            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> boardGameRepository.findByIdForUpdate(game.getId())))
                    .isInstanceOf(PessimisticLockingFailureException.class)
                    .rootCause().hasMessageContaining("lock timeout");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(elapsedMillis).isBetween(2_500L, 6_000L);
        }
    }

    @Test
    @DisplayName("락을 잡은 트랜잭션이 끝나면(커밋) 대기하던 쪽이 타임아웃 없이 락을 얻는다")
    void lockReleasedOnCommit_waiterProceeds() throws Exception {
        BoardGame game = saveGame();
        try (LockHolder ignored = new LockHolder(game.getId(), 500)) {
            Long id = tx.execute(status -> boardGameRepository.findByIdForUpdate(game.getId()).orElseThrow().getId());

            assertThat(id).isEqualTo(game.getId());
        }
    }

    @Test
    @DisplayName("read-only 트랜잭션에서는 PostgreSQL 이 SELECT FOR UPDATE 를 거부한다 (H2 는 통과 → 락 메서드는 쓰기 트랜잭션에서만)")
    void forUpdateRejectedInReadOnlyTransaction() {
        BoardGame game = saveGame();
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        assertThatThrownBy(() -> readOnly.executeWithoutResult(
                status -> boardGameRepository.findByIdForUpdate(game.getId())))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("read-only transaction");
    }

    @Test
    @DisplayName("락 대기가 3초를 넘으면 예약 신청 API 가 409 + RESERVATION_BUSY 메시지로 응답한다 (GlobalExceptionHandler 변환)")
    void createApi_lockTimeout_returns409Busy() throws Exception {
        Member owner = saveAdmin("owner");
        Member guest = saveMember("guest");
        BoardGame game = saveOfflineGame(owner, 2);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String body = """
                {"boardGameId": %d, "startDate": "%s", "endDate": "%s"}
                """.formatted(game.getId(), tomorrow, tomorrow);

        try (LockHolder ignored = new LockHolder(game.getId(), 30_000)) {
            mockMvc.perform(post("/api/reservations")
                            .with(loginAs(guest))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(ErrorCode.RESERVATION_BUSY.getMessage()));
        }

        assertThat(reservationRepository.count()).isZero(); // 타임아웃된 신청은 저장되지 않는다
    }

    // ───────────── 같은 락 공유 ─────────────

    @Test
    @DisplayName("예약 신청·게임 수정(재고)·숨기기는 같은 게임 행 락을 쓴다: 다른 트랜잭션이 락을 쥐고 있는 동안 세 경로 모두 기다렸다가 성공한다")
    void createUpdateHide_shareTheSameGameLock() throws Exception {
        Member owner = saveAdmin("owner");
        Member guest = saveMember("guest");
        BoardGame game = saveOfflineGame(owner, 3);
        Long id = game.getId();

        long createMs;
        try (LockHolder ignored = new LockHolder(id, 700)) {
            createMs = elapsedMillis(() -> reservationService.create(guest.getId(), reservationRequest(game, 0, 0)));
        }
        long updateMs;
        try (LockHolder ignored = new LockHolder(id, 700)) {
            updateMs = elapsedMillis(() -> boardGameService.update(id, owner.getId(), gameRequest(3), null));
        }
        long hideMs;
        try (LockHolder ignored = new LockHolder(id, 700)) {
            hideMs = elapsedMillis(() -> boardGameService.changeVisibility(id, owner.getId(), false));
        }

        // 락을 쥔 쪽이 최대 700ms 잡고 있었으므로 그 대부분을 기다렸어야 하고, 3초 타임아웃에는 걸리지 않는다
        assertThat(createMs).isBetween(400L, 3_000L);
        assertThat(updateMs).isBetween(400L, 3_000L);
        assertThat(hideMs).isBetween(400L, 3_000L);
    }

    // ───────────── 섞여서 동시에 올 때의 불변식 ─────────────

    @Test
    @DisplayName("재고 3 게임에 신청 30건과 재고 줄이기(3→1)가 동시에 와도, 어떤 날짜도 최종 재고를 넘지 않는다")
    void reduceStockRacingWithCreates_neverOverbooks() throws Exception {
        Member owner = saveAdmin("owner");
        BoardGame game = saveOfflineGame(owner, 3);
        List<Supplier<?>> tasks = new ArrayList<>();
        for (Member member : saveMembers("m", 30)) {
            tasks.add(() -> reservationService.create(member.getId(), reservationRequest(game, 0, 1)));
        }
        int updateIndex = 15;
        tasks.add(updateIndex, () -> boardGameService.update(game.getId(), owner.getId(), gameRequest(1), null));

        List<ErrorCode> results = runConcurrently(tasks);

        // 재고 줄이기는 성공(그 시점 점유 ≤ 1)하거나 STOCK_BELOW_RESERVED 로 거절된다. 신청 쪽은 성공 아니면 NOT_AVAILABLE
        ErrorCode updateResult = results.get(updateIndex);
        assertThat(updateResult).isIn(null, ErrorCode.STOCK_BELOW_RESERVED);
        int finalStock = boardGameRepository.findById(game.getId()).orElseThrow().getStock();
        assertThat(finalStock).isEqualTo(updateResult == null ? 1 : 3);

        List<Reservation> reservations = reservationRepository.findAll();
        assertThat(reservations).isNotEmpty();
        assertThat(successCount(results) - (updateResult == null ? 1 : 0)).isEqualTo(reservations.size());
        for (int offset = 0; offset < 3; offset++) {
            assertThat(occupancyOn(reservations, tomorrow.plusDays(offset)))
                    .as("점유 수 @ 내일+%d (최종 재고 %d)", offset, finalStock)
                    .isLessThanOrEqualTo(finalStock);
        }
    }

    @Test
    @DisplayName("신청 30건과 숨기기가 동시에 와도, 숨긴 뒤에는 활성 예약이 남지 않는다 (먼저 성공한 예약은 GAME_SUSPENDED 로 취소, 이후 신청은 거절)")
    void hideRacingWithCreates_leavesNoActiveReservation() throws Exception {
        Member owner = saveAdmin("owner");
        BoardGame game = saveOfflineGame(owner, 5);
        List<Supplier<?>> tasks = new ArrayList<>();
        for (Member member : saveMembers("m", 30)) {
            tasks.add(() -> reservationService.create(member.getId(), reservationRequest(game, 0, 1)));
        }
        int hideIndex = 10;
        tasks.add(hideIndex, () -> boardGameService.changeVisibility(game.getId(), owner.getId(), false));

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(results.get(hideIndex)).isNull();
        assertThat(boardGameRepository.findById(game.getId()).orElseThrow().isVisible()).isFalse();
        // 신청은 성공(이후 숨기기가 취소) / 재고 부족 / 숨김 게임 중 하나
        long created = successCount(results) - 1; // 숨기기 성공 1건 제외
        assertThat(created + count(results, ErrorCode.NOT_AVAILABLE) + count(results, ErrorCode.BOARDGAME_NOT_AVAILABLE))
                .isEqualTo(30);
        List<Reservation> reservations = reservationRepository.findAll();
        assertThat(reservations).hasSize((int) created);
        assertThat(reservations).allSatisfy(reservation -> {
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(reservation.getCancelReason()).isEqualTo(CancelReason.GAME_SUSPENDED);
        });
    }

    // ───────────── 전체 흐름 (PG 에서 read-only·트랜잭션 오류 없이) ─────────────

    @Test
    @DisplayName("전체 흐름: 신청 → 승인 → 달력 점유 → 취소 → 복구, 신청 → 거절, 신청 → 재고 줄이기 → 숨기기(GAME_SUSPENDED 취소) 가 PG 에서 그대로 동작한다")
    void fullFlow_onPostgres() {
        Member owner = saveAdmin("owner");
        Member guest = saveMember("guest");
        BoardGame game = saveOfflineGame(owner, 2);
        Long id = game.getId();

        ReservationResponse first = reservationService.create(guest.getId(), reservationRequest(game, 0, 1));
        assertThat(first.status()).isEqualTo(ReservationStatus.PENDING);

        AdminReservationResponse approved = reservationService.approve(owner.getId(), first.id());
        assertThat(approved.status()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(approved.requester().nickname()).isEqualTo("guest");
        // 승인 후에도 점유 유지: 재고 2 - 1 = 1
        assertThat(reservationService.getAvailability(id, tomorrow, tomorrow.plusDays(2)))
                .extracting(AvailabilityResponse::available).containsExactly(1, 1, 2);

        ReservationResponse cancelled = reservationService.cancel(guest.getId(), first.id());
        assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.MEMBER);
        assertThat(reservationService.getAvailability(id, tomorrow, tomorrow.plusDays(2)))
                .extracting(AvailabilityResponse::available).containsExactly(2, 2, 2);

        ReservationResponse second = reservationService.create(guest.getId(), reservationRequest(game, 2, 3));
        AdminReservationResponse rejected = reservationService.reject(owner.getId(), second.id(), "점검 중");
        assertThat(rejected.status()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo("점검 중");

        ReservationResponse third = reservationService.create(guest.getId(), reservationRequest(game, 4, 5));
        assertThat(boardGameService.update(id, owner.getId(), gameRequest(1), null).stock()).isEqualTo(1); // 최대 점유 1 → 가능

        boardGameService.changeVisibility(id, owner.getId(), false);

        List<ReservationResponse> mine = reservationService.findMine(guest.getId(), null);
        assertThat(mine).extracting(ReservationResponse::id).containsExactly(third.id(), second.id(), first.id());
        assertThat(mine.get(0).status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(mine.get(0).cancelReason()).isEqualTo(CancelReason.GAME_SUSPENDED);
        assertThat(mine.get(0).boardGameVisible()).isFalse();
        assertThat(reservationService.findForOwner(owner.getId(), ReservationStatus.PENDING)).isEmpty();
    }
}
