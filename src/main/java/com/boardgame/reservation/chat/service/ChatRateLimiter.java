package com.boardgame.reservation.chat.service;

import com.boardgame.reservation.chat.dto.ChatUsageResponse;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 회원당 하루 호출 횟수 제한(⭐ README 포인트, Redis). 키: chat:usage:{memberId}:{yyyyMMdd}(Asia/Seoul, 주입된 Clock 기준).
 * checkAndIncrement가 쓴 키를 Increment로 돌려주고 decrement가 그 키를 그대로 받아 쓰는 이유:
 * 자정을 걸쳐 실패 처리가 일어나도(체크 시점과 취소 시점의 "오늘"이 다를 수 있음) 엉뚱한 날짜의 카운트를 건드리지 않기 위해서다.
 * Spring 빈 등록은 ChatLlmConfig에서 한다(생성자를 하나로 유지해 테스트에서도 직접 new 하기 쉽게 하기 위함).
 */
public class ChatRateLimiter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int dailyLimit;

    public ChatRateLimiter(StringRedisTemplate redis, Clock clock, int dailyLimit) {
        this.redis = redis;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    public record Increment(String key, long remaining) {
    }

    /** @throws BusinessException CHAT_LIMIT_EXCEEDED — 이미 오늘 한도를 다 썼으면 */
    public Increment checkAndIncrement(Long memberId) {
        String key = key(memberId);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofHours(25));
        }
        if (count == null || count > dailyLimit) {
            redis.opsForValue().decrement(key);
            throw new BusinessException(ErrorCode.CHAT_LIMIT_EXCEEDED);
        }
        return new Increment(key, dailyLimit - count);
    }

    /** LLM 호출이 실패하면 차감을 취소한다(사용자 탓이 아닌 실패는 횟수에서 빼지 않음). checkAndIncrement가 준 key로만 호출할 것 */
    public void decrement(String key) {
        redis.opsForValue().decrement(key);
    }

    /** GET 전용, INCR 없음 */
    public ChatUsageResponse usage(Long memberId) {
        String value = redis.opsForValue().get(key(memberId));
        long used = value == null ? 0 : Long.parseLong(value);
        long remaining = Math.max(dailyLimit - used, 0);
        return new ChatUsageResponse(dailyLimit, used, remaining);
    }

    private String key(Long memberId) {
        return "chat:usage:" + memberId + ":" + LocalDate.now(clock).format(DATE_FORMAT);
    }
}
