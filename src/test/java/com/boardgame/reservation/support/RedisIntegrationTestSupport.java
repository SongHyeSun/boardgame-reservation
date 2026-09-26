package com.boardgame.reservation.support;

import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

/**
 * Redis 가 필요한 통합 테스트(party, member 세션 등)의 공통 부모.
 * - 실제 Redis(redis:7)를 Testcontainers 로 JVM 당 한 번만 띄우고 @ServiceConnection 으로 연결
 * - H2 는 기존 테스트(testdb)와 분리된 redisdb 사용 → 컨텍스트가 달라도 스키마가 서로 안 건드림
 * - Spring Session(Redis)도 켠다: 테스트 application.yml 이 꺼둔 자동설정 exclude 를 빈 값으로 덮어씀
 * - 이 클래스를 상속한 테스트는 properties 가 같아 Spring 컨텍스트·H2·Redis 를 공유한다
 *   → 정리(cleanUp)는 여기서 party/boardgame/member 전부 담당한다.
 * - 매 테스트 후 Redis party:*, spring:session:* 키 삭제, DB 는 party_member → party → boardgame → member 순으로 정리
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:redisdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.autoconfigure.exclude="
})
public abstract class RedisIntegrationTestSupport {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @Autowired
    protected StringRedisTemplate redisTemplate;
    @Autowired
    protected PartyMemberRepository partyMemberRepository;
    @Autowired
    protected PartyRepository partyRepository;
    @Autowired
    protected BoardGameRepository boardGameRepository;
    @Autowired
    protected MemberRepository memberRepository;

    @AfterEach
    protected void cleanUp() {
        for (String pattern : new String[]{"party:*", "spring:session:*"}) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
        partyMemberRepository.deleteAllInBatch();
        partyRepository.deleteAllInBatch();
        boardGameRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }
}
