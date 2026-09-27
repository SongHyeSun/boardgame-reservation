package com.boardgame.reservation.notification.redis;

import com.boardgame.reservation.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림 저장 뒤 Redis "notifications" 채널에 발행한다.
 * 서버가 여러 대일 수 있어(세션 외부화와 같은 이유) 알림을 만든 서버와 사용자가 SSE 로 연결된 서버가 다를 수 있다 — README 포인트.
 * publish 실패가 알림 저장 자체를 실패시키면 안 되므로 예외는 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(Long receiverId, NotificationResponse notification) {
        try {
            String json = objectMapper.writeValueAsString(new NotificationPubSubMessage(receiverId, notification));
            redisTemplate.convertAndSend(NotificationRedisConfig.CHANNEL, json);
        } catch (Exception e) {
            log.error("Redis 알림 publish 실패: receiverId={}", receiverId, e);
        }
    }
}
