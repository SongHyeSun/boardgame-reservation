package com.boardgame.reservation.notification.service;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.notification.domain.Notification;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.redis.NotificationPublisher;
import com.boardgame.reservation.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * notify() 의 행위자=수신자 스킵 규칙(이 프로젝트에서 유일한 스킵 판단 지점)을 집중 검증한다.
 * 리스너별 수신자 계산 자체는 각 *NotificationListenerTest 가 mock 된 NotificationService 호출 인자로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long RECEIVER_ID = 1L;
    private static final Long ACTOR_ID = 2L;

    @Mock
    NotificationRepository notificationRepository;
    @Mock
    MemberRepository memberRepository;
    @Mock
    NotificationPublisher notificationPublisher;

    @InjectMocks
    NotificationService notificationService;

    private static Member member(Long id) {
        Member member = Member.createUser("m" + id + "@test.com", "pw", "nick" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("receiverId 가 null 이면 저장·발행 모두 하지 않는다")
    void notify_skipsWhenReceiverIsNull() {
        notificationService.notify(null, ACTOR_ID, NotificationType.PARTY_JOINED, "메시지", "/parties/1");

        verifyNoInteractions(notificationRepository, notificationPublisher);
    }

    @Test
    @DisplayName("행위자 = 수신자면(본인 행동) 저장·발행 모두 하지 않는다")
    void notify_skipsWhenReceiverEqualsActor() {
        notificationService.notify(RECEIVER_ID, RECEIVER_ID, NotificationType.PARTY_JOINED, "메시지", "/parties/1");

        verifyNoInteractions(notificationRepository, notificationPublisher);
    }

    @Test
    @DisplayName("actorId 가 null 이어도(행위자를 특정할 수 없는 경우) receiverId 가 있으면 정상 저장한다")
    void notify_savesWhenActorIsNull() {
        given(memberRepository.getReferenceById(RECEIVER_ID)).willReturn(member(RECEIVER_ID));
        given(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify(RECEIVER_ID, null, NotificationType.GAME_SUSPENDED, "메시지", "/me/reservations");

        verify(notificationRepository).save(org.mockito.ArgumentMatchers.any(Notification.class));
    }

    @Test
    @DisplayName("행위자 != 수신자면 receiver·type·message·link 그대로 저장하고, 커밋 뒤 Redis 로 발행한다 (여기선 트랜잭션 없어 즉시 실행)")
    void notify_savesAndPublishes() {
        Member receiver = member(RECEIVER_ID);
        given(memberRepository.getReferenceById(RECEIVER_ID)).willReturn(receiver);
        given(notificationRepository.save(org.mockito.ArgumentMatchers.any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));

        notificationService.notify(RECEIVER_ID, ACTOR_ID, NotificationType.PARTY_JOINED,
                "닉네임님이 '파티' 파티에 참여했어요", "/parties/1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getReceiver()).isSameAs(receiver);
        assertThat(saved.getType()).isEqualTo(NotificationType.PARTY_JOINED);
        assertThat(saved.getMessage()).isEqualTo("닉네임님이 '파티' 파티에 참여했어요");
        assertThat(saved.getLink()).isEqualTo("/parties/1");
        assertThat(saved.isRead()).isFalse();
        // 단위 테스트에는 활성 트랜잭션이 없으므로 TransactionCallbacks.afterCommit 이 즉시 실행된다
        verify(notificationPublisher).publish(org.mockito.ArgumentMatchers.eq(RECEIVER_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("본인 것만 읽음 처리할 수 있다 (남의 알림·존재하지 않는 알림은 NOTIFICATION_NOT_FOUND)")
    void markRead_othersOrMissing_throwsNotFound() {
        given(notificationRepository.findByIdAndReceiverId(10L, RECEIVER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(RECEIVER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("본인 알림이면 읽음 처리된다")
    void markRead_own_marksRead() {
        Notification notification = Notification.create(member(RECEIVER_ID), NotificationType.PARTY_JOINED, "메시지", "/parties/1");
        given(notificationRepository.findByIdAndReceiverId(10L, RECEIVER_ID)).willReturn(Optional.of(notification));

        notificationService.markRead(RECEIVER_ID, 10L);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("전체 읽음은 리포지토리의 벌크 업데이트를 그대로 호출한다")
    void markAllRead_delegatesToRepository() {
        notificationService.markAllRead(RECEIVER_ID);

        verify(notificationRepository).markAllRead(RECEIVER_ID);
        verify(notificationRepository, never()).findByIdAndReceiverId(anyLong(), anyLong());
    }
}
