package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.party.domain.Party;
import com.boardgame.reservation.party.event.PartyClosedEvent;
import com.boardgame.reservation.party.event.PartyMemberJoinedEvent;
import com.boardgame.reservation.party.event.PartyMemberKickedEvent;
import com.boardgame.reservation.party.event.PartyMemberLeftEvent;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 수신자·메시지·link 계산만 검증한다 (행위자=수신자 스킵 자체는 NotificationServiceTest). */
@ExtendWith(MockitoExtension.class)
class PartyNotificationListenerTest {

    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long PARTY_ID = 10L;

    @Mock
    PartyRepository partyRepository;
    @Mock
    PartyMemberRepository partyMemberRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    PartyNotificationListener listener;

    private static Member member(Long id, String nickname) {
        Member member = Member.createUser(nickname + "@test.com", "pw", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static Party party(Member host) {
        BoardGame boardGame = BoardGame.create("카탄", 2, 4, 60, Difficulty.NORMAL, "설명");
        Party party = Party.create(boardGame, host, "같이 해요", "설명", 4, null);
        ReflectionTestUtils.setField(party, "id", PARTY_ID);
        return party;
    }

    private void verifyNotified(Long receiverId, Long actorId, NotificationType type, String messageFragment, String link) {
        verify(notificationService).notify(eq(receiverId), eq(actorId), eq(type), contains(messageFragment), eq(link));
    }

    @Test
    @DisplayName("참여 알림: 호스트에게 '{닉네임}님이 ...참여했어요', remaining>0 이면 PARTY_FULL 은 발행하지 않는다")
    void onJoined_notifiesHost_noFullWhenRemainingPositive() {
        Party party = party(member(HOST_ID, "호스트"));
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID, "참여자")));

        listener.onJoined(new PartyMemberJoinedEvent(PARTY_ID, MEMBER_ID, 1L));

        verifyNotified(HOST_ID, MEMBER_ID, NotificationType.PARTY_JOINED, "참여자님이", "/parties/" + PARTY_ID);
        verify(notificationService, never()).notify(eq(HOST_ID), eq(MEMBER_ID), eq(NotificationType.PARTY_FULL),
                org.mockito.ArgumentMatchers.anyString(), eq("/parties/" + PARTY_ID));
    }

    @Test
    @DisplayName("참여로 정원이 차면(remaining=0) PARTY_JOINED 에 이어 PARTY_FULL 도 호스트에게 발행한다")
    void onJoined_alsoNotifiesFullWhenRemainingZero() {
        Party party = party(member(HOST_ID, "호스트"));
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID, "참여자")));

        listener.onJoined(new PartyMemberJoinedEvent(PARTY_ID, MEMBER_ID, 0L));

        verifyNotified(HOST_ID, MEMBER_ID, NotificationType.PARTY_JOINED, "참여했어요", "/parties/" + PARTY_ID);
        verifyNotified(HOST_ID, MEMBER_ID, NotificationType.PARTY_FULL, "정원이 모두 찼어요", "/parties/" + PARTY_ID);
    }

    @Test
    @DisplayName("파티나 회원을 찾을 수 없으면(삭제 경합 등) 조용히 아무것도 하지 않는다")
    void onJoined_missingPartyOrMember_doesNothing() {
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.empty());

        listener.onJoined(new PartyMemberJoinedEvent(PARTY_ID, MEMBER_ID, 0L));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("탈퇴 알림: 호스트에게 '{닉네임}님이 ...나갔어요'")
    void onLeft_notifiesHost() {
        Party party = party(member(HOST_ID, "호스트"));
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));
        given(memberRepository.findById(MEMBER_ID)).willReturn(Optional.of(member(MEMBER_ID, "참여자")));

        listener.onLeft(new PartyMemberLeftEvent(PARTY_ID, MEMBER_ID));

        verifyNotified(HOST_ID, MEMBER_ID, NotificationType.PARTY_LEFT, "참여자님이", "/parties/" + PARTY_ID);
    }

    @Test
    @DisplayName("내보내기 알림: 수신자는 내보내진 회원 본인, actor 는 호스트")
    void onKicked_notifiesKickedMember() {
        Party party = party(member(HOST_ID, "호스트"));
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));

        listener.onKicked(new PartyMemberKickedEvent(PARTY_ID, MEMBER_ID));

        verifyNotified(MEMBER_ID, HOST_ID, NotificationType.PARTY_KICKED, "내보내졌어요", "/parties/" + PARTY_ID);
    }

    @Test
    @DisplayName("마감 알림: JOINED 전원에게 발행한다(호스트 본인 포함 — 스킵은 notify() 가 처리)")
    void onClosed_notifiesEveryJoinedMemberIncludingHost() {
        Party party = party(member(HOST_ID, "호스트"));
        given(partyRepository.findWithDetailsById(PARTY_ID)).willReturn(Optional.of(party));
        given(partyMemberRepository.findJoinedMemberIdsByPartyId(PARTY_ID)).willReturn(List.of(HOST_ID, MEMBER_ID));

        listener.onClosed(new PartyClosedEvent(PARTY_ID));

        verifyNotified(HOST_ID, HOST_ID, NotificationType.PARTY_CLOSED, "마감됐어요", "/parties/" + PARTY_ID);
        verifyNotified(MEMBER_ID, HOST_ID, NotificationType.PARTY_CLOSED, "마감됐어요", "/parties/" + PARTY_ID);
    }
}
