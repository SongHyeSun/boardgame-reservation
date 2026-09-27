package com.boardgame.reservation.notification.service;

import com.boardgame.reservation.global.common.TransactionCallbacks;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.notification.domain.Notification;
import com.boardgame.reservation.notification.domain.NotificationType;
import com.boardgame.reservation.notification.dto.NotificationResponse;
import com.boardgame.reservation.notification.redis.NotificationPublisher;
import com.boardgame.reservation.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 알림 저장(리스너가 호출)·조회·읽음 처리(REST 가 호출)를 함께 담당한다.
 * notify() 가 행위자=수신자 스킵을 한 곳에서 처리하므로, 리스너는 타입별 조건 분기 없이 자연스러운 수신자를
 * (목록이면 전부) 그대로 넘기면 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final NotificationPublisher notificationPublisher;

    /**
     * 알림 저장. receiverId 가 없거나 actorId 와 같으면(행위자 본인) 조용히 no-op.
     * 반드시 REQUIRES_NEW 여야 한다 — AFTER_COMMIT 리스너 안에서는 원 트랜잭션의 리소스가 아직 스레드에
     * 바인딩된 채로 남아 있다(물리적 커밋은 이미 끝났지만 TransactionSynchronizationManager 의 정리는 그 뒤).
     * 여기서 REQUIRED/SUPPORTS 로 "참여"하면 이미 끝난(다시 커밋될 일이 없는) 그 트랜잭션에 얹혀서 저장이
     * 조용히 플러시되지 않고 사라진다(예외도 없이) — RestrictedTransactionalEventListenerFactory 가
     * @TransactionalEventListener 메서드 자체에 REQUIRES_NEW/NOT_SUPPORTED 만 허용하는 것과 같은 이유다.
     * 저장이 실제로 커밋된 뒤에만(afterCommit) Redis 로 발행한다 — 커밋 전에 SSE 가 먼저 도착하는 경합을 없앤다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long receiverId, Long actorId, NotificationType type, String message, String link) {
        if (receiverId == null || Objects.equals(receiverId, actorId)) {
            return;
        }
        Notification saved = notificationRepository.save(Notification.create(
                memberRepository.getReferenceById(receiverId), type, message, link));
        TransactionCallbacks.afterCommit(() -> notificationPublisher.publish(receiverId, NotificationResponse.from(saved)));
    }

    public Page<NotificationResponse> findMine(Long memberId, Pageable pageable) {
        return notificationRepository.findByReceiverIdOrderByIdDesc(memberId, pageable).map(NotificationResponse::from);
    }

    public long countUnread(Long memberId) {
        return notificationRepository.countByReceiverIdAndReadFalse(memberId);
    }

    @Transactional
    public void markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(Long memberId) {
        notificationRepository.markAllRead(memberId);
    }
}
