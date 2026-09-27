package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.event.BoardGameSuspendedEvent;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import com.boardgame.reservation.reservation.domain.Reservation;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 취소된 파티·예약의 수신자 계산만 검증한다. */
@ExtendWith(MockitoExtension.class)
class BoardGameNotificationListenerTest {

    private static final Long GAME_ID = 100L;
    private static final Long OWNER_ID = 1L;
    private static final Long PARTY_ID = 10L;
    private static final Long RESERVATION_ID = 20L;
    private static final Long RESERVATION_MEMBER_ID = 3L;

    @Mock
    BoardGameRepository boardGameRepository;
    @Mock
    PartyRepository partyRepository;
    @Mock
    PartyMemberRepository partyMemberRepository;
    @Mock
    ReservationRepository reservationRepository;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    BoardGameNotificationListener listener;

    private static Member member(Long id, String nickname) {
        Member member = Member.createUser(nickname + "@test.com", "pw", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static BoardGame boardGame(Member owner) {
        BoardGame boardGame = BoardGame.create(
                new BoardGame.Details("카탄", 2, 4, 60, Difficulty.NORMAL, "설명", true, false, 1), owner);
        ReflectionTestUtils.setField(boardGame, "id", GAME_ID);
        return boardGame;
    }

    private static Party party(Member host) {
        Party party = Party.create(boardGame(host), host, "같이 해요", "설명", 4, null);
        ReflectionTestUtils.setField(party, "id", PARTY_ID);
        return party;
    }

    private void verifyNotified(Long receiverId, Long actorId, String messageFragment, String link) {
        verify(notificationService).notify(eq(receiverId), eq(actorId), eq(NotificationType.GAME_SUSPENDED),
                contains(messageFragment), eq(link));
    }

    @Test
    @DisplayName("취소된 파티의 JOINED 전원에게, 게임 이름·파티 제목을 담은 GAME_SUSPENDED 알림을 발행한다")
    void onSuspended_notifiesCancelledPartyMembers() {
        Member owner = member(OWNER_ID, "관리자");
        given(boardGameRepository.findWithOwnerById(GAME_ID)).willReturn(Optional.of(boardGame(owner)));
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party(owner)));
        given(partyMemberRepository.findJoinedMemberIdsByPartyId(PARTY_ID)).willReturn(List.of(5L, 6L));

        listener.onSuspended(new BoardGameSuspendedEvent(GAME_ID, List.of(PARTY_ID), List.of()));

        verifyNotified(5L, OWNER_ID, "파티가 취소됐어요", "/parties/" + PARTY_ID);
        verifyNotified(6L, OWNER_ID, "파티가 취소됐어요", "/parties/" + PARTY_ID);
    }

    @Test
    @DisplayName("취소된 예약의 신청자에게 GAME_SUSPENDED 알림을 발행한다 (link=/me/reservations)")
    void onSuspended_notifiesCancelledReservationRequester() {
        Member owner = member(OWNER_ID, "관리자");
        Member requester = member(RESERVATION_MEMBER_ID, "신청자");
        Reservation reservation = Reservation.create(boardGame(owner), requester, LocalDate.now(), LocalDate.now());
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
        given(boardGameRepository.findWithOwnerById(GAME_ID)).willReturn(Optional.of(boardGame(owner)));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));

        listener.onSuspended(new BoardGameSuspendedEvent(GAME_ID, List.of(), List.of(RESERVATION_ID)));

        verifyNotified(RESERVATION_MEMBER_ID, OWNER_ID, "예약이 취소됐어요", "/me/reservations");
    }

    @Test
    @DisplayName("게임을 찾을 수 없으면 조용히 아무것도 하지 않는다")
    void onSuspended_missingGame_doesNothing() {
        given(boardGameRepository.findWithOwnerById(GAME_ID)).willReturn(Optional.empty());

        listener.onSuspended(new BoardGameSuspendedEvent(GAME_ID, List.of(PARTY_ID), List.of(RESERVATION_ID)));

        verifyNoInteractions(notificationService, partyRepository, reservationRepository);
    }
}
