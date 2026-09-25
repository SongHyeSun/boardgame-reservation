package com.boardgame.reservation.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * @CreatedDate 자동 입력 활성화.
 * (메인 Application 클래스에 붙이지 않고 분리해 두면 @WebMvcTest 같은 슬라이스 테스트가 깨지지 않는다)
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
