package com.boardgame.reservation.support;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.repository.BoardGameRepository;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.member.repository.MemberRepository;
import com.boardgame.reservation.party.repository.PartyMemberRepository;
import com.boardgame.reservation.party.repository.PartyRepository;
import com.boardgame.reservation.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.IntStream;

/**
 * DB 를 쓰는 통합 테스트의 공통 부모 (@SpringBootTest·컨테이너는 하위 클래스가 정한다).
 * - RedisIntegrationTestSupport: H2 + Redis 컨테이너
 * - PostgresIntegrationTestSupport: PostgreSQL 컨테이너 (락 검증용)
 * 매 테스트 후 FK 역순(reservation → party_member → party → board_game → member)으로 데이터를 지운다.
 * JUnit5 는 하위 클래스의 @AfterEach 가 먼저 실행되므로, Redis 키 정리가 DB 정리보다 앞선다.
 */
public abstract class DatabaseTestSupport {

    @Autowired
    protected PartyMemberRepository partyMemberRepository;
    @Autowired
    protected PartyRepository partyRepository;
    @Autowired
    protected BoardGameRepository boardGameRepository;
    @Autowired
    protected MemberRepository memberRepository;
    @Autowired
    protected ReservationRepository reservationRepository;

    protected Member saveMember(String name) {
        return memberRepository.save(Member.createUser(name + "@test.com", "pw", name));
    }

    protected Member saveAdmin(String name) {
        return memberRepository.save(Member.createAdmin(name + "@test.com", "pw", name));
    }

    /** prefix0 ~ prefix{count-1} 회원을 한 번에 저장 (동시성 테스트용) */
    protected List<Member> saveMembers(String prefix, int count) {
        return memberRepository.saveAll(IntStream.range(0, count)
                .mapToObj(i -> Member.createUser(prefix + i + "@test.com", "pw", prefix + i))
                .toList());
    }

    /** owner 가 등록한 오프라인 전용 게임(재고 stock, 보이는 상태) */
    protected BoardGame saveOfflineGame(Member owner, int stock) {
        return boardGameRepository.save(BoardGame.create(
                new BoardGame.Details("Catan", 3, 4, 60, Difficulty.NORMAL, "설명", true, false, stock), owner));
    }

    @AfterEach
    protected void cleanUpDatabase() {
        reservationRepository.deleteAllInBatch();
        partyMemberRepository.deleteAllInBatch();
        partyRepository.deleteAllInBatch();
        boardGameRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }
}
