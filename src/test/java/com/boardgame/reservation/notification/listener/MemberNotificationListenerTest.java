package com.boardgame.reservation.notification.listener;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.event.AdminRequestDecidedEvent;
import com.boardgame.reservation.member.event.AdminRequestedEvent;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** SUPER_ADMIN 조회(app.admin.email)·수신자 계산만 검증한다. */
@ExtendWith(MockitoExtension.class)
class MemberNotificationListenerTest {

    private static final Long REQUESTER_ID = 1L;
    private static final Long SUPER_ADMIN_ID = 99L;
    private static final String ADMIN_EMAIL = "root@test.com";

    @Mock
    MemberRepository memberRepository;
    @Mock
    NotificationService notificationService;

    @InjectMocks
    MemberNotificationListener listener;

    private static Member member(Long id, String nickname) {
        Member member = Member.createUser(nickname + "@test.com", "pw", nickname);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("신청 알림: app.admin.email 로 찾은 SUPER_ADMIN 에게 '{닉네임}님이 ...신청했어요'")
    void onRequested_notifiesSuperAdmin() {
        ReflectionTestUtils.setField(listener, "adminEmail", ADMIN_EMAIL);
        Member requester = member(REQUESTER_ID, "신청자");
        given(memberRepository.findById(REQUESTER_ID)).willReturn(Optional.of(requester));
        given(memberRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.of(member(SUPER_ADMIN_ID, "관리자")));

        listener.onRequested(new AdminRequestedEvent(REQUESTER_ID));

        verify(notificationService).notify(eq(SUPER_ADMIN_ID), eq(REQUESTER_ID), eq(NotificationType.ADMIN_REQUESTED),
                contains("신청자님이 관리자 권한을 신청했어요"), eq("/admin/admin-requests"));
    }

    @Test
    @DisplayName("app.admin.email 이 비어 있으면(설정 누락) superAdminId 는 null 로 notify() 에 넘어간다 (notify() 가 no-op 처리)")
    void onRequested_blankAdminEmail_passesNullReceiver() {
        ReflectionTestUtils.setField(listener, "adminEmail", "");
        given(memberRepository.findById(REQUESTER_ID)).willReturn(Optional.of(member(REQUESTER_ID, "신청자")));

        listener.onRequested(new AdminRequestedEvent(REQUESTER_ID));

        verify(notificationService).notify(isNull(), eq(REQUESTER_ID), eq(NotificationType.ADMIN_REQUESTED),
                contains("신청했어요"), eq("/admin/admin-requests"));
    }

    @Test
    @DisplayName("승인 알림: 신청자에게 ADMIN_APPROVED, link=/me")
    void onDecided_approved_notifiesRequester() {
        ReflectionTestUtils.setField(listener, "adminEmail", ADMIN_EMAIL);
        given(memberRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.of(member(SUPER_ADMIN_ID, "관리자")));

        listener.onDecided(new AdminRequestDecidedEvent(REQUESTER_ID, true));

        verify(notificationService).notify(eq(REQUESTER_ID), eq(SUPER_ADMIN_ID), eq(NotificationType.ADMIN_APPROVED),
                contains("승인됐어요"), eq("/me"));
    }

    @Test
    @DisplayName("거절 알림: 신청자에게 ADMIN_REJECTED, link=/me")
    void onDecided_rejected_notifiesRequester() {
        ReflectionTestUtils.setField(listener, "adminEmail", ADMIN_EMAIL);
        given(memberRepository.findByEmail(ADMIN_EMAIL)).willReturn(Optional.of(member(SUPER_ADMIN_ID, "관리자")));

        listener.onDecided(new AdminRequestDecidedEvent(REQUESTER_ID, false));

        verify(notificationService).notify(eq(REQUESTER_ID), eq(SUPER_ADMIN_ID), eq(NotificationType.ADMIN_REJECTED),
                contains("거절됐어요"), eq("/me"));
    }

    @Test
    @DisplayName("신청자를 찾을 수 없으면 조용히 아무것도 하지 않는다")
    void onRequested_missingRequester_doesNothing() {
        given(memberRepository.findById(REQUESTER_ID)).willReturn(Optional.empty());

        listener.onRequested(new AdminRequestedEvent(REQUESTER_ID));

        verifyNoInteractions(notificationService);
    }
}
