package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.event.ReservationCancelledEvent;
import com.boardgame.reservation.reservation.event.ReservationDecidedEvent;
import com.boardgame.reservation.reservation.event.ReservationRequestedEvent;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약 신청·승인/거절·취소 → 게임 소유 관리자 ↔ 신청자 알림.
 * ReservationRepository.findById 는 join fetch 가 없어 member·boardGame 이 지연 프록시로 남는다.
 * 닉네임·게임 이름처럼 id 가 아닌 필드를 읽어야 해서(단순 id 접근과 달리 세션이 필요) NotificationReadContext 로
 * 읽기 전용 트랜잭션을 열고, 그 호출 자체를 try 안에 두어 트랜잭션 획득 실패(커넥션 풀 고갈 등)도 여기서 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationNotificationListener {

    private static final String OWNER_LINK = "/admin/reservations";
    private static final String MEMBER_LINK = "/me/reservations";

    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;
    private final NotificationReadContext readContext;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(ReservationRequestedEvent event) {
        try {
            readContext.run(() -> {
                Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
                if (reservation == null) {
                    return;
                }
                Long requesterId = reservation.getMember().getId();
                Long ownerId = reservation.getBoardGame().getCreatedBy() == null
                        ? null : reservation.getBoardGame().getCreatedBy().getId();
                String message = reservation.getMember().getNickname() + "님이 '" + reservation.getBoardGame().getName()
                        + "' 대여를 신청했어요 (" + period(reservation) + ")";
                notificationService.notify(ownerId, requesterId, NotificationType.RESERVATION_REQUESTED, message, OWNER_LINK);
            });
        } catch (Exception e) {
            log.error("RESERVATION_REQUESTED 알림 실패: reservationId={}", event.reservationId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(ReservationDecidedEvent event) {
        try {
            readContext.run(() -> {
                Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
                if (reservation == null) {
                    return;
                }
                Long requesterId = reservation.getMember().getId();
                Long ownerId = reservation.getBoardGame().getCreatedBy() == null
                        ? null : reservation.getBoardGame().getCreatedBy().getId();
                String gameName = reservation.getBoardGame().getName();

                if (event.approved()) {
                    notificationService.notify(requesterId, ownerId, NotificationType.RESERVATION_APPROVED,
                            "'" + gameName + "' 대여 예약이 승인됐어요", MEMBER_LINK);
                } else {
                    String reasonSuffix = reservation.getRejectReason() == null ? "" : " (" + reservation.getRejectReason() + ")";
                    notificationService.notify(requesterId, ownerId, NotificationType.RESERVATION_REJECTED,
                            "'" + gameName + "' 대여 예약이 거절됐어요" + reasonSuffix, MEMBER_LINK);
                }
            });
        } catch (Exception e) {
            log.error("RESERVATION_APPROVED/REJECTED 알림 실패: reservationId={}", event.reservationId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(ReservationCancelledEvent event) {
        try {
            readContext.run(() -> {
                Reservation reservation = reservationRepository.findById(event.reservationId()).orElse(null);
                if (reservation == null) {
                    return;
                }
                Long requesterId = reservation.getMember().getId();
                Long ownerId = reservation.getBoardGame().getCreatedBy() == null
                        ? null : reservation.getBoardGame().getCreatedBy().getId();
                String message = reservation.getMember().getNickname() + "님이 '" + reservation.getBoardGame().getName()
                        + "' 예약을 취소했어요";
                notificationService.notify(ownerId, requesterId, NotificationType.RESERVATION_CANCELLED, message, OWNER_LINK);
            });
        } catch (Exception e) {
            log.error("RESERVATION_CANCELLED 알림 실패: reservationId={}", event.reservationId(), e);
        }
    }

    /** 당일 대여(start==end)는 날짜 하나만, 아니면 시작~종료 */
    private static String period(Reservation reservation) {
        return reservation.getStartDate().equals(reservation.getEndDate())
                ? reservation.getStartDate().toString()
                : reservation.getStartDate() + " ~ " + reservation.getEndDate();
    }
}
