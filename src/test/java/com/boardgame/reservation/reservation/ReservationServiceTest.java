package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.dto.AdminReservationResponse;
import com.boardgame.reservation.reservation.dto.AvailabilityResponse;
import com.boardgame.reservation.reservation.dto.ReservationCreateRequest;
import com.boardgame.reservation.reservation.dto.ReservationResponse;
import com.boardgame.reservation.reservation.event.ReservationCancelledEvent;
import com.boardgame.reservation.reservation.event.ReservationDecidedEvent;
import com.boardgame.reservation.reservation.event.ReservationRequestedEvent;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import com.boardgame.reservation.reservation.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * DB/스프링 없이 예약 서비스 규칙만 검증하는 단위 테스트 (고정 Clock: 2026-09-27 12:00 Asia/Seoul).
 * 락·동시성은 여기서 다루지 않는다 → ReservationConcurrencyTest / ReservationLockPostgresTest (실제 PostgreSQL).
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 12, 0);
    private static final long OWNER_ID = 7L;
    private static final long GUEST_ID = 11L;
    private static final long OTHER_ID = 12L;
    private static final long GAME_ID = 1L;

    @Mock
    ReservationRepository reservationRepository;
    @Mock
    BoardGameRepository boardGameRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;

    ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository, boardGameRepository, memberRepository, eventPublisher, CLOCK);
    }

    // ───────────── fixture ─────────────

    private static Member member(long id) {
        Member member = Member.createAdmin("u" + id + "@test.com", "pw", "user" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    /** id 1, 오프라인 전용, 보이는 게임. 소유자는 OWNER_ID */
    private static BoardGame game(int stock) {
        BoardGame game = BoardGame.create(
                new BoardGame.Details("Catan", 3, 4, 60, Difficulty.NORMAL, "설명", true, false, stock), member(OWNER_ID));
        ReflectionTestUtils.setField(game, "id", GAME_ID);
        return game;
    }

    private static BoardGame hiddenGame() {
        BoardGame game = game(2);
        game.hide();
        return game;
    }

    private static BoardGame onlineOnlyGame() {
        BoardGame game = BoardGame.create(
                new BoardGame.Details("Codenames", 2, 8, 20, Difficulty.EASY, "온라인", false, true, 0), member(OWNER_ID));
        ReflectionTestUtils.setField(game, "id", GAME_ID);
        return game;
    }

    /** 오늘 기준 startOffset~endOffset 일 뒤 예약 (id, 신청자 지정) */
    private static Reservation reservation(long id, BoardGame game, long memberId, int startOffset, int endOffset) {
        Reservation reservation = Reservation.create(
                game, member(memberId), TODAY.plusDays(startOffset), TODAY.plusDays(endOffset));
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private static ReservationCreateRequest request(int startOffset, int endOffset) {
        return new ReservationCreateRequest(GAME_ID, TODAY.plusDays(startOffset), TODAY.plusDays(endOffset));
    }

    private void givenLockedGame(BoardGame game) {
        given(boardGameRepository.findByIdForUpdate(GAME_ID)).willReturn(Optional.of(game));
    }

    private void givenLockedReservation(Reservation reservation) {
        given(reservationRepository.findByIdForUpdate(reservation.getId())).willReturn(Optional.of(reservation));
    }

    private static BusinessException thrown(Runnable action) {
        return (BusinessException) org.assertj.core.api.Assertions.catchThrowable(action::run);
    }

    // ───────────── 신청 ─────────────

    @Test
    @DisplayName("신청 성공: PENDING 으로 저장되고 ReservationRequestedEvent 가 발행된다")
    void create_success() {
        givenLockedGame(game(2));
        given(memberRepository.findById(GUEST_ID)).willReturn(Optional.of(member(GUEST_ID)));
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY.plusDays(1), TODAY.plusDays(2)))
                .willReturn(List.of());
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });

        ReservationResponse response = reservationService.create(GUEST_ID, request(1, 2));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.startDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(response.endDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(response.boardGameName()).isEqualTo("Catan");
        verify(eventPublisher).publishEvent(new ReservationRequestedEvent(100L));
    }

    @Test
    @DisplayName("신청은 게임 행 락(findByIdForUpdate)으로 게임을 읽는다 (락 없는 findById 를 쓰지 않는다)")
    void create_usesLockedRead() {
        givenLockedGame(game(2));
        given(memberRepository.findById(GUEST_ID)).willReturn(Optional.of(member(GUEST_ID)));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        reservationService.create(GUEST_ID, request(0, 0));

        verify(boardGameRepository).findByIdForUpdate(GAME_ID);
        verify(boardGameRepository, never()).findById(any());
    }

    @Test
    @DisplayName("기간 규칙 위반은 락을 잡기 전에 INVALID_RESERVATION_PERIOD (과거·역순·60일 초과·7일 초과)")
    void create_invalidPeriod_beforeLock() {
        List<ReservationCreateRequest> invalid = List.of(
                request(-1, 0),   // 과거 시작
                request(3, 2),    // start > end
                request(61, 61),  // 시작이 60일 초과
                request(0, 7));   // 8일

        for (ReservationCreateRequest request : invalid) {
            assertThat(thrown(() -> reservationService.create(GUEST_ID, request)).getErrorCode())
                    .as("%s ~ %s", request.startDate(), request.endDate())
                    .isEqualTo(ErrorCode.INVALID_RESERVATION_PERIOD);
        }

        verifyNoInteractions(boardGameRepository, reservationRepository, memberRepository, eventPublisher);
    }

    @Test
    @DisplayName("경계값: 오늘 당일·오늘+60일 시작·7일 기간은 신청할 수 있다")
    void create_boundaryPeriods_ok() {
        givenLockedGame(game(2));
        given(memberRepository.findById(GUEST_ID)).willReturn(Optional.of(member(GUEST_ID)));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));

        reservationService.create(GUEST_ID, request(0, 0));
        reservationService.create(GUEST_ID, request(60, 60));
        reservationService.create(GUEST_ID, request(10, 16));

        verify(eventPublisher, org.mockito.Mockito.times(3)).publishEvent(any(ReservationRequestedEvent.class));
    }

    @Test
    @DisplayName("없는 게임은 BOARDGAME_NOT_FOUND")
    void create_gameNotFound() {
        given(boardGameRepository.findByIdForUpdate(GAME_ID)).willReturn(Optional.empty());

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 1))).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);
    }

    @Test
    @DisplayName("숨김(운영 중지) 게임은 BOARDGAME_NOT_AVAILABLE")
    void create_hiddenGame() {
        givenLockedGame(hiddenGame());

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 1))).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_AVAILABLE);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("온라인 전용 게임은 RESERVATION_NOT_SUPPORTED (409)")
    void create_onlineOnlyGame() {
        givenLockedGame(onlineOnlyGame());

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 1))).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_SUPPORTED);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 회원이 같은 게임에 기간이 겹치는 활성 예약이 있으면 DUPLICATE_RESERVATION (재고가 남아 있어도)")
    void create_duplicateForSameMember() {
        BoardGame game = game(5);
        givenLockedGame(game);
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY.plusDays(2), TODAY.plusDays(3)))
                .willReturn(List.of(reservation(50L, game, GUEST_ID, 1, 2)));

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(2, 3))).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESERVATION);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("중복이면서 재고도 없으면 DUPLICATE_RESERVATION 이 먼저다 (본인 예약 때문에 없는 것)")
    void create_duplicateWinsOverSoldOut() {
        BoardGame game = game(1);
        givenLockedGame(game);
        given(reservationRepository.findActiveOverlapping(any(), any(), any()))
                .willReturn(List.of(reservation(50L, game, GUEST_ID, 1, 2)));

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 2))).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESERVATION);
    }

    @Test
    @DisplayName("신청 기간 중 하루라도 재고가 다 찼으면 NOT_AVAILABLE (재고 1, 남의 예약이 마지막 날만 겹침)")
    void create_soldOutOnAnyDate() {
        BoardGame game = game(1);
        givenLockedGame(game);
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY.plusDays(1), TODAY.plusDays(3)))
                .willReturn(List.of(reservation(50L, game, OTHER_ID, 3, 5)));

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 3))).getErrorCode())
                .isEqualTo(ErrorCode.NOT_AVAILABLE);
        verify(reservationRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("재고 2 에서 남의 예약 1건이 일부만 겹치면 신청할 수 있고, 2건이 겹치는 날이 있으면 NOT_AVAILABLE")
    void create_stock2_partialOverlap() {
        BoardGame game = game(2);
        givenLockedGame(game);
        given(memberRepository.findById(GUEST_ID)).willReturn(Optional.of(member(GUEST_ID)));
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> invocation.getArgument(0));
        // 남의 예약: A(+1~+2), B(+2~+3) → +2 일에 2건
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY.plusDays(3), TODAY.plusDays(4)))
                .willReturn(List.of(reservation(50L, game, OTHER_ID, 2, 3)));
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY.plusDays(1), TODAY.plusDays(2)))
                .willReturn(List.of(reservation(50L, game, OTHER_ID, 1, 2), reservation(51L, game, 13L, 2, 3)));

        ReservationResponse ok = reservationService.create(GUEST_ID, request(3, 4)); // +3 일에 1건 < 2

        assertThat(ok.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 2))).getErrorCode()) // +2 일에 2건 = 2
                .isEqualTo(ErrorCode.NOT_AVAILABLE);
    }

    @Test
    @DisplayName("회원이 없으면 MEMBER_NOT_FOUND")
    void create_memberNotFound() {
        givenLockedGame(game(2));
        given(memberRepository.findById(GUEST_ID)).willReturn(Optional.empty());

        assertThat(thrown(() -> reservationService.create(GUEST_ID, request(1, 1))).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // ───────────── 승인 · 거절 ─────────────

    @Test
    @DisplayName("소유 관리자가 승인하면 APPROVED + decidedAt 기록, ReservationDecidedEvent(approved=true) 발행")
    void approve_success() {
        Reservation reservation = reservation(60L, game(2), GUEST_ID, 1, 2);
        givenLockedReservation(reservation);

        AdminReservationResponse response = reservationService.approve(OWNER_ID, 60L);

        assertThat(response.status()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(response.requester().nickname()).isEqualTo("user" + GUEST_ID);
        assertThat(reservation.getDecidedAt()).isEqualTo(NOW);
        verify(eventPublisher).publishEvent(new ReservationDecidedEvent(60L, true));
    }

    @Test
    @DisplayName("소유 관리자가 아니면 승인·거절 모두 NOT_GAME_OWNER (상태·이벤트 변화 없음)")
    void decide_notOwner() {
        Reservation reservation = reservation(60L, game(2), GUEST_ID, 1, 2);
        givenLockedReservation(reservation);

        assertThat(thrown(() -> reservationService.approve(OTHER_ID, 60L)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GAME_OWNER);
        assertThat(thrown(() -> reservationService.reject(OTHER_ID, 60L, "사유")).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GAME_OWNER);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("등록 관리자가 없는(레거시) 게임의 예약은 아무도 승인할 수 없다 (NOT_GAME_OWNER)")
    void approve_legacyGameWithoutOwner() {
        BoardGame legacy = BoardGame.create("Old", 2, 4, 30, Difficulty.EASY, "옛날 게임");
        Reservation reservation = reservation(60L, legacy, GUEST_ID, 1, 2);
        givenLockedReservation(reservation);

        assertThat(thrown(() -> reservationService.approve(OWNER_ID, 60L)).getErrorCode())
                .isEqualTo(ErrorCode.NOT_GAME_OWNER);
    }

    @Test
    @DisplayName("PENDING 이 아닌 예약은 승인·거절할 수 없다 (INVALID_RESERVATION_STATUS)")
    void decide_notPending() {
        Reservation approved = reservation(60L, game(2), GUEST_ID, 1, 2);
        approved.approve(NOW);
        Reservation cancelled = reservation(61L, game(2), GUEST_ID, 3, 4);
        cancelled.cancel(CancelReason.MEMBER);
        givenLockedReservation(approved);
        givenLockedReservation(cancelled);

        assertThat(thrown(() -> reservationService.approve(OWNER_ID, 60L)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS);
        assertThat(thrown(() -> reservationService.reject(OWNER_ID, 60L, null)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS);
        assertThat(thrown(() -> reservationService.approve(OWNER_ID, 61L)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RESERVATION_STATUS);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("없는 예약은 RESERVATION_NOT_FOUND")
    void decide_notFound() {
        given(reservationRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThat(thrown(() -> reservationService.approve(OWNER_ID, 99L)).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
        assertThat(thrown(() -> reservationService.reject(OWNER_ID, 99L, null)).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    @Test
    @DisplayName("거절: REJECTED + 사유 저장, ReservationDecidedEvent(approved=false) 발행. 사유는 선택")
    void reject_success() {
        Reservation withReason = reservation(60L, game(2), GUEST_ID, 1, 2);
        Reservation withoutReason = reservation(61L, game(2), GUEST_ID, 3, 4);
        givenLockedReservation(withReason);
        givenLockedReservation(withoutReason);

        AdminReservationResponse rejected = reservationService.reject(OWNER_ID, 60L, "재고 점검 중");
        reservationService.reject(OWNER_ID, 61L, null);

        assertThat(rejected.status()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo("재고 점검 중");
        assertThat(withReason.getDecidedAt()).isEqualTo(NOW);
        assertThat(withoutReason.getRejectReason()).isNull();
        verify(eventPublisher).publishEvent(new ReservationDecidedEvent(60L, false));
        verify(eventPublisher).publishEvent(new ReservationDecidedEvent(61L, false));
    }

    @Test
    @DisplayName("승인·거절·취소는 예약 행 락(findByIdForUpdate)으로 예약을 읽는다")
    void statusChanges_useReservationRowLock() {
        Reservation reservation = reservation(60L, game(2), GUEST_ID, 1, 2);
        givenLockedReservation(reservation);

        reservationService.cancel(GUEST_ID, 60L);

        verify(reservationRepository).findByIdForUpdate(60L);
        verify(reservationRepository, never()).findById(any());
    }

    // ───────────── 본인 취소 ─────────────

    @Test
    @DisplayName("본인이 시작일 전날까지 취소하면 CANCELLED(MEMBER), ReservationCancelledEvent 발행 (PENDING·APPROVED 모두)")
    void cancel_success() {
        Reservation pending = reservation(60L, game(2), GUEST_ID, 1, 2);
        Reservation approved = reservation(61L, game(2), GUEST_ID, 5, 6);
        approved.approve(NOW);
        givenLockedReservation(pending);
        givenLockedReservation(approved);

        ReservationResponse first = reservationService.cancel(GUEST_ID, 60L);
        ReservationResponse second = reservationService.cancel(GUEST_ID, 61L);

        assertThat(first.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(first.cancelReason()).isEqualTo(CancelReason.MEMBER);
        assertThat(second.status()).isEqualTo(ReservationStatus.CANCELLED);
        verify(eventPublisher).publishEvent(new ReservationCancelledEvent(60L));
        verify(eventPublisher).publishEvent(new ReservationCancelledEvent(61L));
    }

    @Test
    @DisplayName("남의 예약을 취소하면 존재를 숨기려고 RESERVATION_NOT_FOUND (상태 변화 없음)")
    void cancel_notMine() {
        Reservation reservation = reservation(60L, game(2), GUEST_ID, 1, 2);
        givenLockedReservation(reservation);

        assertThat(thrown(() -> reservationService.cancel(OTHER_ID, 60L)).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("시작일 당일·이미 시작한 예약은 CANNOT_CANCEL_RESERVATION, 내일 시작이면 오늘까지 취소 가능")
    void cancel_untilDayBeforeStart() {
        Reservation startsToday = reservation(60L, game(2), GUEST_ID, 0, 1);
        Reservation ongoing = reservation(61L, game(2), GUEST_ID, -1, 1);
        Reservation startsTomorrow = reservation(62L, game(2), GUEST_ID, 1, 1);
        givenLockedReservation(startsToday);
        givenLockedReservation(ongoing);
        givenLockedReservation(startsTomorrow);

        assertThat(thrown(() -> reservationService.cancel(GUEST_ID, 60L)).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_CANCEL_RESERVATION);
        assertThat(thrown(() -> reservationService.cancel(GUEST_ID, 61L)).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_CANCEL_RESERVATION);
        assertThat(reservationService.cancel(GUEST_ID, 62L).status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 거절·취소된 예약은 CANNOT_CANCEL_RESERVATION")
    void cancel_inactive() {
        Reservation rejected = reservation(60L, game(2), GUEST_ID, 3, 4);
        rejected.reject(null, NOW);
        Reservation cancelled = reservation(61L, game(2), GUEST_ID, 5, 6);
        cancelled.cancel(CancelReason.GAME_SUSPENDED);
        givenLockedReservation(rejected);
        givenLockedReservation(cancelled);

        assertThat(thrown(() -> reservationService.cancel(GUEST_ID, 60L)).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_CANCEL_RESERVATION);
        assertThat(thrown(() -> reservationService.cancel(GUEST_ID, 61L)).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_CANCEL_RESERVATION);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("없는 예약 취소는 RESERVATION_NOT_FOUND")
    void cancel_notFound() {
        given(reservationRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThat(thrown(() -> reservationService.cancel(GUEST_ID, 99L)).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_FOUND);
    }

    // ───────────── 달력 가용 조회 ─────────────

    @Test
    @DisplayName("가용 수량 = 재고 - 그 날짜의 활성 예약 수: 재고 2, 부분 겹침 → 2,1,0,0,0,2, 락은 잡지 않는다")
    void availability_stock2_partialOverlaps() {
        BoardGame game = game(2);
        given(boardGameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY, TODAY.plusDays(5))).willReturn(List.of(
                reservation(50L, game, 21L, 1, 3), reservation(51L, game, 22L, 2, 4), reservation(52L, game, 23L, 4, 4)));

        List<AvailabilityResponse> result = reservationService.getAvailability(GAME_ID, TODAY, TODAY.plusDays(5));

        assertThat(result).extracting(AvailabilityResponse::date)
                .containsExactly(TODAY, TODAY.plusDays(1), TODAY.plusDays(2), TODAY.plusDays(3), TODAY.plusDays(4), TODAY.plusDays(5));
        assertThat(result).extracting(AvailabilityResponse::available).containsExactly(2, 1, 0, 0, 0, 2);
        verify(boardGameRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("재고를 줄인 뒤 점유가 재고보다 많아도 가용 수량은 0 미만이 되지 않는다")
    void availability_neverNegative() {
        BoardGame game = game(1);
        given(boardGameRepository.findById(GAME_ID)).willReturn(Optional.of(game));
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY, TODAY)).willReturn(List.of(
                reservation(50L, game, 21L, 0, 0), reservation(51L, game, 22L, 0, 0)));

        assertThat(reservationService.getAvailability(GAME_ID, TODAY, TODAY))
                .extracting(AvailabilityResponse::available).containsExactly(0);
    }

    @Test
    @DisplayName("조회 범위는 양끝 포함 62일까지, 초과·역순이면 INVALID_INPUT (게임·예약은 조회하지 않는다)")
    void availability_rangeValidation() {
        assertThat(thrown(() -> reservationService.getAvailability(GAME_ID, TODAY, TODAY.plusDays(62))).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        assertThat(thrown(() -> reservationService.getAvailability(GAME_ID, TODAY.plusDays(1), TODAY)).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(boardGameRepository, reservationRepository);
    }

    @Test
    @DisplayName("조회 범위 62일(from~to 양끝 포함)은 허용된다")
    void availability_62daysAllowed() {
        given(boardGameRepository.findById(GAME_ID)).willReturn(Optional.of(game(2)));
        given(reservationRepository.findActiveOverlapping(GAME_ID, TODAY, TODAY.plusDays(61))).willReturn(List.of());

        assertThat(reservationService.getAvailability(GAME_ID, TODAY, TODAY.plusDays(61))).hasSize(62);
    }

    @Test
    @DisplayName("없는 게임 404, 숨김 게임 BOARDGAME_NOT_AVAILABLE, 온라인 전용 게임 RESERVATION_NOT_SUPPORTED")
    void availability_gameStates() {
        given(boardGameRepository.findById(99L)).willReturn(Optional.empty());
        assertThat(thrown(() -> reservationService.getAvailability(99L, TODAY, TODAY)).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_FOUND);

        given(boardGameRepository.findById(GAME_ID)).willReturn(Optional.of(hiddenGame()));
        assertThat(thrown(() -> reservationService.getAvailability(GAME_ID, TODAY, TODAY)).getErrorCode())
                .isEqualTo(ErrorCode.BOARDGAME_NOT_AVAILABLE);

        given(boardGameRepository.findById(GAME_ID)).willReturn(Optional.of(onlineOnlyGame()));
        assertThat(thrown(() -> reservationService.getAvailability(GAME_ID, TODAY, TODAY)).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_SUPPORTED);
    }

    // ───────────── 목록 ─────────────

    @Test
    @DisplayName("내 예약: status 가 없으면 전체, 있으면 그 상태만 조회한다")
    void findMine_statusFilter() {
        BoardGame game = game(2);
        Reservation newer = reservation(61L, game, GUEST_ID, 3, 4);
        Reservation older = reservation(60L, game, GUEST_ID, 1, 2);
        given(reservationRepository.findByMemberIdOrderByIdDesc(GUEST_ID)).willReturn(List.of(newer, older));
        given(reservationRepository.findByMemberIdAndStatusOrderByIdDesc(GUEST_ID, ReservationStatus.APPROVED))
                .willReturn(List.of());

        assertThat(reservationService.findMine(GUEST_ID, null)).extracting(ReservationResponse::id)
                .containsExactly(61L, 60L);
        assertThat(reservationService.findMine(GUEST_ID, ReservationStatus.APPROVED)).isEmpty();
    }

    @Test
    @DisplayName("관리자 목록: status 를 생략하면 승인 대기(PENDING), 신청자 정보가 함께 내려간다")
    void findForOwner_defaultsToPending() {
        given(reservationRepository.findByGameOwner(OWNER_ID, ReservationStatus.PENDING))
                .willReturn(List.of(reservation(60L, game(2), GUEST_ID, 1, 2)));
        given(reservationRepository.findByGameOwner(OWNER_ID, ReservationStatus.REJECTED)).willReturn(List.of());

        List<AdminReservationResponse> pending = reservationService.findForOwner(OWNER_ID, null);

        assertThat(pending).singleElement().satisfies(item -> {
            assertThat(item.requester().id()).isEqualTo(GUEST_ID);
            assertThat(item.boardGameName()).isEqualTo("Catan");
        });
        assertThat(reservationService.findForOwner(OWNER_ID, ReservationStatus.REJECTED)).isEmpty();
    }
}
