package com.boardgame.reservation.chat;

import com.boardgame.reservation.chat.dto.ChatUsageResponse;
import com.boardgame.reservation.chat.service.ChatRateLimiter;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisIntegrationTestSupport의 공유 Redis 컨테이너(protected redisTemplate)를 재사용하되,
 * ChatRateLimiter는 원하는 Clock.fixed(...)를 직접 넣어 new 한다(날짜별로 다른 고정 시각이 필요해서).
 */
class ChatRateLimiterTest extends RedisIntegrationTestSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock DAY1 = Clock.fixed(Instant.parse("2026-09-27T03:00:00Z"), SEOUL); // 2026-09-27 12:00 KST
    private static final Clock DAY1_LATE = Clock.fixed(Instant.parse("2026-09-27T14:59:59Z"), SEOUL); // 2026-09-27 23:59:59 KST
    private static final Clock DAY2_EARLY = Clock.fixed(Instant.parse("2026-09-27T15:00:01Z"), SEOUL); // 2026-09-28 00:00:01 KST

    private static final long MEMBER_ID = 1L;

    @Test
    @DisplayName("20회까지 성공, 21번째는 CHAT_LIMIT_EXCEEDED")
    void allowsUpToDailyLimit_thenRejects() {
        ChatRateLimiter limiter = new ChatRateLimiter(redisTemplate, DAY1, 20);
        for (int i = 0; i < 20; i++) {
            limiter.checkAndIncrement(MEMBER_ID);
        }

        assertThatThrownBy(() -> limiter.checkAndIncrement(MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_LIMIT_EXCEEDED);

        ChatUsageResponse usage = limiter.usage(MEMBER_ID);
        assertThat(usage.used()).isEqualTo(20);
        assertThat(usage.remaining()).isZero();
    }

    @Test
    @DisplayName("LLM 실패 시 checkAndIncrement가 준 key로 decrement하면 자정을 걸쳐도 정확히 취소된다")
    void decrement_usesSameKeyAsIncrement_evenAcrossMidnight() {
        ChatRateLimiter limiter = new ChatRateLimiter(redisTemplate, DAY1_LATE, 20);
        ChatRateLimiter.Increment increment = limiter.checkAndIncrement(MEMBER_ID);
        assertThat(limiter.usage(MEMBER_ID).used()).isEqualTo(1);

        limiter.decrement(increment.key());

        assertThat(limiter.usage(MEMBER_ID).used()).isZero();
    }

    @Test
    @DisplayName("날짜가 바뀌면(Clock 기준) 카운트가 초기화된다")
    void resetsWhenDateChanges() {
        ChatRateLimiter day1Limiter = new ChatRateLimiter(redisTemplate, DAY1, 20);
        ChatRateLimiter day2Limiter = new ChatRateLimiter(redisTemplate, DAY2_EARLY, 20);
        for (int i = 0; i < 20; i++) {
            day1Limiter.checkAndIncrement(MEMBER_ID);
        }
        assertThatThrownBy(() -> day1Limiter.checkAndIncrement(MEMBER_ID)).isInstanceOf(BusinessException.class);

        ChatRateLimiter.Increment increment = day2Limiter.checkAndIncrement(MEMBER_ID);

        assertThat(increment.remaining()).isEqualTo(19);
        assertThat(day2Limiter.usage(MEMBER_ID).used()).isEqualTo(1);
    }

    @Test
    @DisplayName("usage는 GET만 하고 INCR하지 않는다")
    void usage_doesNotIncrement() {
        ChatRateLimiter limiter = new ChatRateLimiter(redisTemplate, DAY1, 20);

        limiter.usage(MEMBER_ID);
        limiter.usage(MEMBER_ID);

        assertThat(limiter.usage(MEMBER_ID).used()).isZero();
    }
}
