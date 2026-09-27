package com.boardgame.reservation.notification.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 구독 컨테이너. RedisMessageListenerContainer 는 SmartLifecycle 이라 컨텍스트 기동 시
 * 자동으로 start() 되며 그 자리에서 실제 Redis 구독 연결을 동기로 시도한다 — 진짜 Redis 가 없는 테스트 컨텍스트
 * (PostgresIntegrationTestSupport 등)에서는 컨텍스트 기동 자체가 실패하므로, 이 빈은
 * app.notification.pubsub.enabled=true 인 곳(운영, RedisIntegrationTestSupport 계열)에서만 만든다.
 */
@Configuration
public class NotificationRedisConfig {

    public static final String CHANNEL = "notifications";

    @Bean
    @ConditionalOnProperty(prefix = "app.notification.pubsub", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer notificationListenerContainer(
            RedisConnectionFactory connectionFactory, NotificationSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(CHANNEL));
        return container;
    }
}
