package com.boardgame.reservation.reservation.service;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationOccupancy;
import com.boardgame.reservation.reservation.domain.ReservationPolicy;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.dto.AdminReservationResponse;
import com.boardgame.reservation.reservation.dto.AvailabilityResponse;
import com.boardgame.reservation.reservation.dto.ReservationCreateRequest;
import com.boardgame.reservation.reservation.dto.ReservationResponse;
import com.boardgame.reservation.reservation.event.ReservationCancelledEvent;
import com.boardgame.reservation.reservation.event.ReservationDecidedEvent;
import com.boardgame.reservation.reservation.event.ReservationRequestedEvent;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기간 대여 예약. 승인 전(PENDING)도 재고를 점유한다.
 *
 * 동시성: 신청은 게임 행 비관적 락(BoardGameRepository.findByIdForUpdate) 안에서 "가용 재고 확인 → INSERT" 를 한다.
 * 재고 수정·오프라인 끄기·숨기기(BoardGameService)도 같은 락을 잡아 게임 단위로 직렬화된다.
 * 승인·거절·취소는 예약 행을 잠가(ReservationRepository.findByIdForUpdate) 같은 예약에 대한 동시 처리를 직렬화한다.
 * 락 대기 한도(3초)를 넘으면 PessimisticLockingFailureException → GlobalExceptionHandler 가 RESERVATION_BUSY 로 변환한다.
 * 락 메서드는 쓰기 트랜잭션에서만 호출한다 (PostgreSQL 은 read-only 트랜잭션의 FOR UPDATE 를 거부).
 * 날짜 판단은 Clock(Asia/Seoul) 기준이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final BoardGameRepository boardGameRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 날짜별 남은 수량. 락 없이 읽는 표시용 값이고, 신청 시점의 최종 판단은 create 가 락 안에서 한다 */
    public List<AvailabilityResponse> getAvailability(Long boardGameId, LocalDate from, LocalDate to) {
        ReservationPolicy.validateAvailabilityRange(from, to);
        BoardGame boardGame = boardGameRepository.findById(boardGameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
        ensureReservable(boardGame);

        List<Reservation> active = reservationRepository.findActiveOverlapping(boardGameId, from, to);
        return ReservationOccupancy.countByDate(active, from, to).entrySet().stream()
                .map(entry -> new AvailabilityResponse(entry.getKey(), Math.max(0, boardGame.getStock() - entry.getValue())))
                .toList();
    }

    @Transactional
    public ReservationResponse create(Long memberId, ReservationCreateRequest request) {
        LocalDate start = request.startDate();
        LocalDate end = request.endDate();
        ReservationPolicy.validatePeriod(start, end, LocalDate.now(clock)); // 순수 검증은 락 전에

        BoardGame boardGame = boardGameRepository.findByIdForUpdate(request.boardGameId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARDGAME_NOT_FOUND));
        ensureReservable(boardGame);

        // 락을 잡은 뒤의 조회라 앞선 트랜잭션이 커밋한 예약까지 보인다 (READ COMMITTED)
        List<Reservation> active = reservationRepository.findActiveOverlapping(boardGame.getId(), start, end);
        if (active.stream().anyMatch(reservation -> reservation.isRequestedBy(memberId))) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESERVATION);
        }
        boolean soldOut = ReservationOccupancy.countByDate(active, start, end).values().stream()
                .anyMatch(count -> count >= boardGame.getStock());
        if (soldOut) {
            throw new BusinessException(ErrorCode.NOT_AVAILABLE);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        Reservation saved = reservationRepository.save(Reservation.create(boardGame, member, start, end));
        eventPublisher.publishEvent(new ReservationRequestedEvent(saved.getId()));
        return ReservationResponse.from(saved);
    }

    /** 본인 예약만 취소할 수 있다 (남의 예약은 존재를 숨기려고 404). 시작일 전날까지, 활성 상태만 */
    @Transactional
    public ReservationResponse cancel(Long memberId, Long reservationId) {
        Reservation reservation = findForUpdateOrThrow(reservationId);
        if (!reservation.isRequestedBy(memberId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!reservation.canBeCancelledByMember(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_RESERVATION);
        }
        reservation.cancel(CancelReason.MEMBER);
        eventPublisher.publishEvent(new ReservationCancelledEvent(reservation.getId()));
        return ReservationResponse.from(reservation);
    }

    /** 승인 시 재고 재검사는 필요 없다 (신청 때 이미 점유) */
    @Transactional
    public AdminReservationResponse approve(Long ownerId, Long reservationId) {
        Reservation reservation = findOwnedForUpdateOrThrow(reservationId, ownerId);
        reservation.approve(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new ReservationDecidedEvent(reservation.getId(), true));
        return AdminReservationResponse.from(reservation);
    }

    /** 거절되면 재고 점유가 풀려 달력에서 다시 선택할 수 있다 */
    @Transactional
    public AdminReservationResponse reject(Long ownerId, Long reservationId, String reason) {
        Reservation reservation = findOwnedForUpdateOrThrow(reservationId, ownerId);
        reservation.reject(reason, LocalDateTime.now(clock));
        eventPublisher.publishEvent(new ReservationDecidedEvent(reservation.getId(), false));
        return AdminReservationResponse.from(reservation);
    }

    /** 내 예약 최신순. status 가 null 이면 전체 */
    public List<ReservationResponse> findMine(Long memberId, ReservationStatus status) {
        List<Reservation> reservations = status == null
                ? reservationRepository.findByMemberIdOrderByIdDesc(memberId)
                : reservationRepository.findByMemberIdAndStatusOrderByIdDesc(memberId, status);
        return reservations.stream().map(ReservationResponse::from).toList();
    }

    /** 내가 등록한 게임의 예약 최신순. status 를 생략하면 승인 대기(PENDING) */
    public List<AdminReservationResponse> findForOwner(Long ownerId, ReservationStatus status) {
        ReservationStatus filter = status == null ? ReservationStatus.PENDING : status;
        return reservationRepository.findByGameOwner(ownerId, filter).stream()
                .map(AdminReservationResponse::from)
                .toList();
    }

    // ───────────── 내부 헬퍼 ─────────────

    /** 숨김 게임과 온라인 전용 게임은 대여할 수 없다 */
    private void ensureReservable(BoardGame boardGame) {
        if (!boardGame.isVisible()) {
            throw new BusinessException(ErrorCode.BOARDGAME_NOT_AVAILABLE);
        }
        if (!boardGame.isOfflineAvailable()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_SUPPORTED);
        }
    }

    private Reservation findForUpdateOrThrow(Long reservationId) {
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    private Reservation findOwnedForUpdateOrThrow(Long reservationId, Long ownerId) {
        Reservation reservation = findForUpdateOrThrow(reservationId);
        if (!reservation.getBoardGame().isOwnedBy(ownerId)) {
            throw new BusinessException(ErrorCode.NOT_GAME_OWNER);
        }
        return reservation;
    }
}
