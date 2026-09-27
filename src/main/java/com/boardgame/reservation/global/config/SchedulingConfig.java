package com.boardgame.reservation.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 활성화 (SseEmitterRepository 의 25초 heartbeat 용).
 * (메인 Application 클래스에 붙이지 않고 분리해 두면 @WebMvcTest 같은 슬라이스 테스트가 깨지지 않는다 — JpaAuditingConfig 와 동일한 이유)
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
