package com.boardgame.reservation.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 PostgreSQL 이 필요한 통합 테스트의 공통 부모 (비관적 락·락 타임아웃·read-only 트랜잭션 제약 검증용).
 * 나머지 테스트는 H2(RedisIntegrationTestSupport 계열)를 그대로 쓴다.
 * - postgres:16 을 Testcontainers 로 JVM 당 한 번만 띄우고 @ServiceConnection 으로 연결한다.
 *   ConnectionDetails 가 spring.datasource.*(테스트 application.yml 의 H2 URL)보다 우선하므로 URL 을 덮어쓸 필요가 없다.
 * - 스키마는 테스트 application.yml 의 ddl-auto: create-drop 으로 컨테이너 DB 에 생성된다 (전체 엔티티 DDL 이 실제 PG 에서 검증됨).
 * - Redis 는 쓰지 않는다. 세션 자동설정은 테스트 yml 대로 exclude(MockMvc 는 loginAs 로 인증).
 * - 이 클래스를 상속한 테스트는 같은 Spring 컨텍스트·컨테이너를 공유한다 → 상속하는 쪽에서 @SpringBootTest 프로퍼티·@MockitoBean 을 바꾸지 말 것.
 * - DB 정리·saveMember/saveAdmin 은 DatabaseTestSupport.
 * - Hikari 풀을 ConcurrencyTestUtils.THREADS(32) 보다 넉넉히 키운다: 알림 리스너가 AFTER_COMMIT 에 REQUIRES_NEW 로
 *   커넥션을 하나 더 쓰므로, 기본 풀(10)로는 32 스레드가 동시에 요청을 던지는 동시성 테스트에서 커넥션 대기가 밀려
 *   connectionTimeout 을 넘길 수 있다.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=40")
public abstract class PostgresIntegrationTestSupport extends DatabaseTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }
}
