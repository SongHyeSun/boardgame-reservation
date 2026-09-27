package com.boardgame.reservation.reservation;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.reservation.domain.CancelReason;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 27, 12, 0);

    private static Reservation reservation(int startOffset, int endOffset) {
        BoardGame game = BoardGame.create("Catan", 3, 4, 60, Difficulty.NORMAL, "설명");
        Member member = Member.createUser("a@test.com", "pw", "a");
        ReflectionTestUtils.setField(member, "id", 5L);
        return Reservation.create(game, member, TODAY.plusDays(startOffset), TODAY.plusDays(endOffset));
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("생성 직후는 PENDING 이고 활성(재고 점유) 상태다")
    void create_isPendingAndActive() {
        Reservation reservation = reservation(1, 2);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.isActive()).isTrue();
        assertThat(reservation.getCancelReason()).isNull();
        assertThat(reservation.getRejectReason()).isNull();
        assertThat(reservation.getDecidedAt()).isNull();
    }

    @Test
    @DisplayName("승인: PENDING → APPROVED, decidedAt 기록, 여전히 활성")
    void approve() {
        Reservation reservation = reservation(1, 2);

        reservation.approve(NOW);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(reservation.getDecidedAt()).isEqualTo(NOW);
        assertThat(reservation.isActive()).isTrue();
    }

    @Test
    @DisplayName("승인·거절은 PENDING 에서만: 이미 처리된 예약은 INVALID_RESERVATION_STATUS")
    void decide_onlyPending() {
        Reservation approved = reservation(1, 2);
        approved.approve(NOW);
        Reservation rejected = reservation(1, 2);
        rejected.reject("사유", NOW);
        Reservation cancelled = reservation(1, 2);
        cancelled.cancel(CancelReason.MEMBER);

        assertErrorCode(() -> approved.approve(NOW), ErrorCode.INVALID_RESERVATION_STATUS);
        assertErrorCode(() -> approved.reject(null, NOW), ErrorCode.INVALID_RESERVATION_STATUS);
        assertErrorCode(() -> rejected.approve(NOW), ErrorCode.INVALID_RESERVATION_STATUS);
        assertErrorCode(() -> cancelled.approve(NOW), ErrorCode.INVALID_RESERVATION_STATUS);
    }

    @Test
    @DisplayName("거절: REJECTED, 사유는 앞뒤 공백을 제거해 저장하고 재고 점유가 풀린다")
    void reject_withReason() {
        Reservation reservation = reservation(1, 2);

        reservation.reject("  재고 점검 중  ", NOW);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(reservation.getRejectReason()).isEqualTo("재고 점검 중");
        assertThat(reservation.getDecidedAt()).isEqualTo(NOW);
        assertThat(reservation.isActive()).isFalse();
    }

    @Test
    @DisplayName("거절 사유는 선택: null·공백뿐이면 null 로 저장")
    void reject_blankReasonBecomesNull() {
        Reservation nullReason = reservation(1, 2);
        Reservation blankReason = reservation(1, 2);

        nullReason.reject(null, NOW);
        blankReason.reject("   ", NOW);

        assertThat(nullReason.getRejectReason()).isNull();
        assertThat(blankReason.getRejectReason()).isNull();
    }

    @Test
    @DisplayName("취소: PENDING·APPROVED 모두 가능하고 사유를 기록하며 재고 점유가 풀린다")
    void cancel_fromActive() {
        Reservation pending = reservation(1, 2);
        Reservation approved = reservation(1, 2);
        approved.approve(NOW);

        pending.cancel(CancelReason.MEMBER);
        approved.cancel(CancelReason.GAME_SUSPENDED);

        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(pending.getCancelReason()).isEqualTo(CancelReason.MEMBER);
        assertThat(pending.isActive()).isFalse();
        assertThat(approved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(approved.getCancelReason()).isEqualTo(CancelReason.GAME_SUSPENDED);
    }

    @Test
    @DisplayName("이미 거절·취소된 예약은 취소할 수 없다 (CANNOT_CANCEL_RESERVATION)")
    void cancel_inactiveThrows() {
        Reservation rejected = reservation(1, 2);
        rejected.reject(null, NOW);
        Reservation cancelled = reservation(1, 2);
        cancelled.cancel(CancelReason.MEMBER);

        assertErrorCode(() -> rejected.cancel(CancelReason.MEMBER), ErrorCode.CANNOT_CANCEL_RESERVATION);
        assertErrorCode(() -> cancelled.cancel(CancelReason.GAME_SUSPENDED), ErrorCode.CANNOT_CANCEL_RESERVATION);
    }

    @Test
    @DisplayName("본인 취소 시점: 시작일 전날까지 가능, 시작일 당일·이미 시작한 예약은 불가")
    void canBeCancelledByMember_untilDayBeforeStart() {
        assertThat(reservation(1, 2).canBeCancelledByMember(TODAY)).isTrue();   // 내일 시작 → 오늘까지 가능
        assertThat(reservation(0, 1).canBeCancelledByMember(TODAY)).isFalse();  // 오늘 시작
        assertThat(reservation(-1, 1).canBeCancelledByMember(TODAY)).isFalse(); // 이미 진행 중
    }

    @Test
    @DisplayName("본인 취소 시점: 활성이 아니면(거절·취소) 시점과 무관하게 불가")
    void canBeCancelledByMember_inactiveFalse() {
        Reservation rejected = reservation(5, 6);
        rejected.reject(null, NOW);

        assertThat(rejected.canBeCancelledByMember(TODAY)).isFalse();
    }

    @Test
    @DisplayName("기간 겹침: 하루라도 겹치면 true, 맞닿기만 해도(같은 날) 겹침, 하루 떨어지면 false")
    void overlaps() {
        Reservation reservation = reservation(2, 4); // +2 ~ +4

        assertThat(reservation.overlaps(TODAY.plusDays(4), TODAY.plusDays(6))).isTrue();  // 종료일 = 시작일
        assertThat(reservation.overlaps(TODAY, TODAY.plusDays(2))).isTrue();              // 시작일 = 종료일
        assertThat(reservation.overlaps(TODAY.plusDays(3), TODAY.plusDays(3))).isTrue();  // 안쪽
        assertThat(reservation.overlaps(TODAY, TODAY.plusDays(10))).isTrue();             // 감쌈
        assertThat(reservation.overlaps(TODAY.plusDays(5), TODAY.plusDays(6))).isFalse();
        assertThat(reservation.overlaps(TODAY, TODAY.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("신청자 확인: 회원 id 로 본인 여부를 판단한다")
    void isRequestedBy() {
        Reservation reservation = reservation(1, 2);

        assertThat(reservation.isRequestedBy(5L)).isTrue();
        assertThat(reservation.isRequestedBy(6L)).isFalse();
    }
}
