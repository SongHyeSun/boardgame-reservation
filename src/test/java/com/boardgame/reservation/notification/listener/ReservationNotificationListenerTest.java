package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.event.ReservationCancelledEvent;
import com.boardgame.reservation.reservation.event.ReservationDecidedEvent;
import com.boardgame.reservation.reservation.event.ReservationRequestedEvent;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 수신자·메시지 계산만 검증한다. NotificationReadContext 는 실제로 트랜잭션을 열지 않고 즉시 action 을 실행하도록 스텁한다
 * (여기서 검증하려는 건 그 안의 로직이지 트랜잭션 경계 자체가 아니다 — 트랜잭션 자체는 @SpringBootTest 통합 테스트가 검증).
 */
@ExtendWith(MockitoExtension.class)
class ReservationNotificationListenerTest {

    private static final Long RESERVATION_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    @Mock
    ReservationRepository reservationRepository;
    @Mock
    NotificationService notificationService;
    @Mock
    NotificationReadContext readContext;

    @InjectMocks
    ReservationNotificationListener listener;

    @BeforeEach
    void stubReadContextToRunImmediately() {
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(readContext).run(any());
    }

    private static Member member(Long id, String nickname) {
        Member member = Member.createUser(nickname + "@test.com", "pw", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static BoardGame boardGame(Member owner) {
        return BoardGame.create(new BoardGame.Details("카탄", 2, 4, 60, Difficulty.NORMAL, "설명", true, false, 1), owner);
    }

    private static Reservation reservation(Member owner, Member requester, LocalDate start, LocalDate end) {
        Reservation reservation = Reservation.create(boardGame(owner), requester, start, end);
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
        return reservation;
    }

    private void verifyNotified(Long receiverId, Long actorId, NotificationType type, String messageFragment, String link) {
        verify(notificationService).notify(eq(receiverId), eq(actorId), eq(type), contains(messageFragment), eq(link));
    }

    @Test
    @DisplayName("신청 알림: 게임 소유 관리자에게 '{닉네임}님이 ...신청했어요 (기간)', 당일 대여는 날짜 하나만 표시")
    void onRequested_notifiesOwner_sameDayPeriod() {
        Reservation reservation = reservation(member(OWNER_ID, "관리자"), member(MEMBER_ID, "신청자"),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 5));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        listener.onRequested(new ReservationRequestedEvent(RESERVATION_ID));

        verifyNotified(OWNER_ID, MEMBER_ID, NotificationType.RESERVATION_REQUESTED,
                "신청자님이 '카탄' 대여를 신청했어요 (2026-10-05)", "/admin/reservations");
    }

    @Test
    @DisplayName("여러 날 대여는 기간을 '시작 ~ 종료' 로 표시한다")
    void onRequested_multiDayPeriod() {
        Reservation reservation = reservation(member(OWNER_ID, "관리자"), member(MEMBER_ID, "신청자"),
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        listener.onRequested(new ReservationRequestedEvent(RESERVATION_ID));

        verifyNotified(OWNER_ID, MEMBER_ID, NotificationType.RESERVATION_REQUESTED,
                "2026-10-05 ~ 2026-10-07", "/admin/reservations");
    }

    @Test
    @DisplayName("승인 알림: 신청자에게 RESERVATION_APPROVED, link=/me/reservations")
    void onDecided_approved_notifiesRequester() {
        Reservation reservation = reservation(member(OWNER_ID, "관리자"), member(MEMBER_ID, "신청자"),
                LocalDate.now(), LocalDate.now());
        reservation.approve(java.time.LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        listener.onDecided(new ReservationDecidedEvent(RESERVATION_ID, true));

        verifyNotified(MEMBER_ID, OWNER_ID, NotificationType.RESERVATION_APPROVED, "승인됐어요", "/me/reservations");
    }

    @Test
    @DisplayName("거절 알림: 사유가 있으면 메시지에 괄호로 덧붙인다")
    void onDecided_rejected_includesReason() {
        Reservation reservation = reservation(member(OWNER_ID, "관리자"), member(MEMBER_ID, "신청자"),
                LocalDate.now(), LocalDate.now());
        reservation.reject("재고 부족", java.time.LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        listener.onDecided(new ReservationDecidedEvent(RESERVATION_ID, false));

        verifyNotified(MEMBER_ID, OWNER_ID, NotificationType.RESERVATION_REJECTED,
                "거절됐어요 (재고 부족)", "/me/reservations");
    }

    @Test
    @DisplayName("취소 알림: 게임 소유 관리자에게 '{닉네임}님이 ...취소했어요', link=/admin/reservations")
    void onCancelled_notifiesOwner() {
        Reservation reservation = reservation(member(OWNER_ID, "관리자"), member(MEMBER_ID, "신청자"),
                LocalDate.now(), LocalDate.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        listener.onCancelled(new ReservationCancelledEvent(RESERVATION_ID));

        verifyNotified(OWNER_ID, MEMBER_ID, NotificationType.RESERVATION_CANCELLED,
                "신청자님이 '카탄' 예약을 취소했어요", "/admin/reservations");
    }

    @Test
    @DisplayName("예약을 찾을 수 없으면 조용히 아무것도 하지 않는다")
    void onRequested_missingReservation_doesNothing() {
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        listener.onRequested(new ReservationRequestedEvent(RESERVATION_ID));

        verifyNoInteractions(notificationService);
    }
}
