package com.boardgame.reservation.reservation.domain;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.global.common.BaseTimeEntity;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ERD: RESERVATION (id, board_game_id, member_id, start_date, end_date, status, cancel_reason, reject_reason, decided_at, created_at)
 * 당일 대여는 start_date = end_date. PENDING·APPROVED 가 재고를 점유한다(활성 예약).
 * 반납은 관리하지 않는다: 종료일이 지난 APPROVED 는 화면에서 "대여 완료"로만 표시하고 상태는 바꾸지 않는다.
 * 상태 전이 위반은 엔티티가 BusinessException 으로 막는다. "시작일 전날까지 취소" 같은 시점 규칙은 canBeCancelledByMember.
 */
@Entity
@Table(name = "reservation",
        indexes = @Index(name = "idx_reservation_game_period", columnList = "board_game_id, start_date, end_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_game_id", nullable = false)
    private BoardGame boardGame;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CancelReason cancelReason;

    @Column(length = 100)
    private String rejectReason;

    /** 승인·거절한 시각 */
    private LocalDateTime decidedAt;

    private Reservation(BoardGame boardGame, Member member, LocalDate startDate, LocalDate endDate) {
        this.boardGame = boardGame;
        this.member = member;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ReservationStatus.PENDING;
    }

    /** 기간 정책·재고 검사는 서비스가 끝낸 뒤 호출한다 */
    public static Reservation create(BoardGame boardGame, Member member, LocalDate startDate, LocalDate endDate) {
        return new Reservation(boardGame, member, startDate, endDate);
    }

    public void approve(LocalDateTime decidedAt) {
        requirePending();
        this.status = ReservationStatus.APPROVED;
        this.decidedAt = decidedAt;
    }

    /** reason 은 선택. 공백뿐이면 null 로 저장한다 (길이 제한은 요청 DTO 가 검증) */
    public void reject(String reason, LocalDateTime decidedAt) {
        requirePending();
        this.status = ReservationStatus.REJECTED;
        this.rejectReason = (reason == null || reason.isBlank()) ? null : reason.strip();
        this.decidedAt = decidedAt;
    }

    /** 활성(PENDING·APPROVED)만 취소할 수 있다. 시점 규칙은 canBeCancelledByMember 로 호출자가 확인 */
    public void cancel(CancelReason reason) {
        if (!isActive()) {
            throw new BusinessException(ErrorCode.CANNOT_CANCEL_RESERVATION);
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public boolean isActive() {
        return status.isActive();
    }

    /** 기간이 [start, end] 와 하루라도 겹치는지 */
    public boolean overlaps(LocalDate start, LocalDate end) {
        return !startDate.isAfter(end) && !endDate.isBefore(start);
    }

    /** 본인 취소: 활성 상태이고 시작일 전날까지 (시작일 당일부터는 불가) */
    public boolean canBeCancelledByMember(LocalDate today) {
        return isActive() && today.isBefore(startDate);
    }

    public boolean isRequestedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    private void requirePending() {
        if (status != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATUS);
        }
    }
}
