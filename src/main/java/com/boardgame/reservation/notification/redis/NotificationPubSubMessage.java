package com.boardgame.reservation.notification.redis;

import com.boardgame.reservation.notification.dto.NotificationResponse;

/** Redis Pub/Sub "notifications" 채널에 실어 보내는 JSON 페이로드. */
public record NotificationPubSubMessage(Long receiverId, NotificationResponse notification) {
}
