package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.member.domain.Member;
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
 * Redis 가 필요한 파티 테스트의 공통 부모.
 * - 실제 Redis(redis:7)를 Testcontainers 로 JVM 당 한 번만 띄우고 @ServiceConnection 으로 연결
 * - H2 는 기존 테스트(testdb)와 분리된 partydb 사용 → 컨텍스트가 달라도 스키마가 서로 안 건드림
 * - Spring Session(Redis)도 켠다: 테스트 application.yml 이 꺼둔 자동설정 exclude 를 빈 값으로 덮어씀
 * - 매 테스트 후 Redis party:*, spring:session:* 키 삭제, DB 는 party_member → party → boardgame/member 순으로 정리
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:partydb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.autoconfigure.exclude="
})
abstract class PartyRedisTestSupport {

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
    void cleanUp() {
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

    protected Member saveMember(String name) {
        return memberRepository.save(Member.createUser(name + "@test.com", "pw", name));
    }

    protected BoardGame saveBoardGame(int minPlayers, int maxPlayers) {
        return boardGameRepository.save(
                BoardGame.create("Catan", minPlayers, maxPlayers, 60, Difficulty.NORMAL, "자원 교환"));
    }

    protected String remainingKey(Long partyId) {
        return "party:" + partyId + ":remaining";
    }
}
