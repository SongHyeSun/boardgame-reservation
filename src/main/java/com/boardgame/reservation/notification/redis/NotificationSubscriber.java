package com.boardgame.reservation.notification.redis;

import com.boardgame.reservation.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * "notifications" 채널 구독자. 이 서버 인스턴스가 그 회원의 SseEmitter 를 들고 있으면 전송한다.
 * Message.getBody() 는 StringRedisTemplate 의 직렬화기를 거치지 않은 raw byte 라 UTF-8 로 직접 디코드한다.
 * 처리 실패가 다른 구독 메시지 처리를 막으면 안 되므로 예외는 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSubscriber implements MessageListener {

    private final SseEmitterRepository emitterRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            NotificationPubSubMessage payload = objectMapper.readValue(json, NotificationPubSubMessage.class);
            emitterRepository.send(payload.receiverId(), "notification", payload.notification());
        } catch (Exception e) {
            log.error("Redis 알림 subscribe 처리 실패", e);
        }
    }
}
