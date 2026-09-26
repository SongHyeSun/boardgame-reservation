package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.support.RedisIntegrationTestSupport;

/**
 * 파티 테스트 공통 부모. Redis Testcontainers·세션 복원·정리는 RedisIntegrationTestSupport 가 담당하고,
 * 여기에는 파티 테스트 전용 헬퍼만 둔다.
 */
abstract class PartyRedisTestSupport extends RedisIntegrationTestSupport {

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
