package com.boardgame.reservation.notification;

import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.event.AdminRequestDecidedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ⭐ notification-plan.md §8: 이벤트를 발행한 트랜잭션이 롤백되면 알림이 저장되지 않는다.
 * AdminRequestDecidedEvent 를 쓰는 이유: 리스너(MemberNotificationListener.onDecided)가 별도 조회 없이
 * event.memberId() 를 그대로 수신자로 쓰므로, 저장 여부가 오직 "발행한 트랜잭션이 커밋됐는가"에만 좌우된다.
 */
class NotificationAfterCommitIntegrationTest extends NotificationRedisTestSupport {

    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("⭐ 이벤트를 발행한 트랜잭션이 롤백되면 알림이 저장되지 않는다")
    void rollback_doesNotSaveNotification() {
        Member requester = saveMember("requester");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AdminRequestDecidedEvent(requester.getId(), true));
            throw new RuntimeException("강제 롤백");
        })).hasMessage("강제 롤백");

        assertThat(notificationRepository.findByReceiverIdOrderByIdDesc(requester.getId(), Pageable.unpaged()).getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("대조군: 같은 이벤트라도 트랜잭션이 정상 커밋되면 알림이 저장된다")
    void commit_savesNotification() {
        Member requester = saveMember("requester");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status ->
                eventPublisher.publishEvent(new AdminRequestDecidedEvent(requester.getId(), true)));

        assertThat(notificationRepository.findByReceiverIdOrderByIdDesc(requester.getId(), Pageable.unpaged()).getContent())
                .hasSize(1);
    }
}
