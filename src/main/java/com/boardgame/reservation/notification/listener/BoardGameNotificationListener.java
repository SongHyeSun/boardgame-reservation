package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.event.BoardGameSuspendedEvent;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 게임 운영 중지(숨김)로 취소된 파티·예약 → 취소된 파티 참여자·예약자에게 알림.
 * findWithOwnerById(createdBy EntityGraph)·findWithDetailsById(host·boardGame join fetch)·
 * reservation.getMember().getId()(지연 프록시의 id 는 세션 없이도 안전) 만 쓰므로 트랜잭션이 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoardGameNotificationListener {

    private final BoardGameRepository boardGameRepository;
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSuspended(BoardGameSuspendedEvent event) {
        try {
            BoardGame boardGame = boardGameRepository.findWithOwnerById(event.gameId()).orElse(null);
            if (boardGame == null) {
                return;
            }
            String gameName = boardGame.getName();
            // 등록 관리자가 없는(레거시) 게임은 숨길 수 있는 사람이 없어야 정상이지만, 방어적으로 null 이면 actor 없이 처리
            Long ownerId = boardGame.getCreatedBy() == null ? null : boardGame.getCreatedBy().getId();

            for (Long partyId : event.cancelledPartyIds()) {
                notifyCancelledParty(partyId, gameName, ownerId);
            }
            for (Long reservationId : event.cancelledReservationIds()) {
                notifyCancelledReservation(reservationId, gameName, ownerId);
            }
        } catch (Exception e) {
            log.error("GAME_SUSPENDED 알림 실패: gameId={}", event.gameId(), e);
        }
    }

    private void notifyCancelledParty(Long partyId, String gameName, Long ownerId) {
        Party party = partyRepository.findWithDetailsById(partyId).orElse(null);
        if (party == null) {
            return;
        }
        String message = "'" + gameName + "' 운영 중지로 '" + party.getTitle() + "' 파티가 취소됐어요";
        String link = "/parties/" + partyId;
        for (Long memberId : partyMemberRepository.findJoinedMemberIdsByPartyId(partyId)) {
            notificationService.notify(memberId, ownerId, NotificationType.GAME_SUSPENDED, message, link);
        }
    }

    private void notifyCancelledReservation(Long reservationId, String gameName, Long ownerId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return;
        }
        notificationService.notify(reservation.getMember().getId(), ownerId, NotificationType.GAME_SUSPENDED,
                "'" + gameName + "' 운영 중지로 예약이 취소됐어요", "/me/reservations");
    }
}
