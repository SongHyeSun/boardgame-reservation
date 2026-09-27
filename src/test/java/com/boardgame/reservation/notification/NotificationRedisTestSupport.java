package com.boardgame.reservation.notification;

import com.boardgame.reservation.support.RedisIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * 알림 통합 테스트 공통 부모. RedisIntegrationTestSupport(H2 + 실제 Redis Testcontainer, app.notification.pubsub.enabled=true)
 * 위에 Security 필터 체인을 태운 MockMvc 를 얹는다 — AFTER_COMMIT 리스너의 진짜 커밋·Redis publish 를 검증하려면
 * H2 만으로 충분하고(REQUIRES_NEW 는 Postgres 전용 기능이 아니다), Redis Pub/Sub 만 진짜 컨테이너가 필요하기 때문.
 */
abstract class NotificationRedisTestSupport extends RedisIntegrationTestSupport {

    @Autowired
    protected WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }
}
