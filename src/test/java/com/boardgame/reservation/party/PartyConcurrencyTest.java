package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.party.service.PartyService;
import com.boardgame.reservation.support.PartyRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.boardgame.reservation.support.ConcurrencyTestUtils.count;
import static com.boardgame.reservation.support.ConcurrencyTestUtils.runConcurrently;
import static com.boardgame.reservation.support.ConcurrencyTestUtils.successCount;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 정합성 검증: 서비스를 직접, 진짜 동시에 호출한다 (트랜잭션 없는 테스트 → 각 호출이 독립 커밋).
 * 실제 Redis(Testcontainers) + H2. 동시 실행 헬퍼는 support/ConcurrencyTestUtils (예약 동시성 테스트와 공용).
 */
class PartyConcurrencyTest extends PartyRedisTestSupport {

    @Autowired
    PartyService partyService;

    @Test
    @DisplayName("capacity 5(호스트 포함) 파티에 100명이 동시에 join → 성공 정확히 4, PARTY_FULL 96, DB 5명, Redis remaining 0")
    void join_100Members_capacity5() throws Exception {
        Member host = saveMember("host");
        BoardGame boardGame = saveBoardGame(2, 8);
        Long partyId = partyService.create(host.getId(),
                PartyRequests.boardGame(boardGame.getId(), "선착순", 5)).id();

        List<Member> members = memberRepository.saveAll(
                IntStream.range(0, 100)
                        .mapToObj(i -> Member.createUser("m" + i + "@test.com", "pw", "m" + i))
                        .toList());
        List<Supplier<?>> tasks = members.stream()
                .<Supplier<?>>map(m -> () -> partyService.join(partyId, m.getId()))
                .toList();

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(4);
        assertThat(count(results, ErrorCode.PARTY_FULL)).isEqualTo(96);
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(5);
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("0");
    }

    @Test
    @DisplayName("기타 게임 파티(정원 5, 호스트 포함)에 100명이 동시에 join → 성공 정확히 4, PARTY_FULL 96, DB 5명, Redis remaining 0")
    void join_100Members_customGameParty() throws Exception {
        Member host = saveMember("host");
        Long partyId = partyService.create(host.getId(), PartyRequests.customGame("구스구스덕", "선착순", 5)).id();

        List<Member> members = memberRepository.saveAll(
                IntStream.range(0, 100)
                        .mapToObj(i -> Member.createUser("m" + i + "@test.com", "pw", "m" + i))
                        .toList());
        List<Supplier<?>> tasks = members.stream()
                .<Supplier<?>>map(m -> () -> partyService.join(partyId, m.getId()))
                .toList();

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(4);
        assertThat(count(results, ErrorCode.PARTY_FULL)).isEqualTo(96);
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(5);
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("0");
    }

    @Test
    @DisplayName("내보내진 회원이 20번 동시 join → 전부 KICKED_FROM_PARTY, 자리·참여자는 그대로 (Redis remaining 4, DB JOINED 1)")
    void join_kickedMember_20times() throws Exception {
        Member host = saveMember("host");
        Member guest = saveMember("guest");
        Long partyId = partyService.create(host.getId(), PartyRequests.customGame("롤", "내보내기", 5)).id();
        partyService.join(partyId, guest.getId());
        partyService.kick(partyId, host.getId(), guest.getId());

        List<Supplier<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> partyService.join(partyId, guest.getId()));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(count(results, ErrorCode.KICKED_FROM_PARTY)).isEqualTo(20);
        assertThat(partyMemberRepository.countJoinedByPartyId(partyId)).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("4");
    }

    @Test
    @DisplayName("같은 회원이 10번 동시 join → 성공 1, ALREADY_JOINED 9, 그 회원의 DB 행 1")
    void join_sameMember_10times() throws Exception {
        Member host = saveMember("host");
        Member guest = saveMember("guest");
        BoardGame boardGame = saveBoardGame(2, 8);
        Long partyId = partyService.create(host.getId(),
                PartyRequests.boardGame(boardGame.getId(), "중복 방어", 5)).id();

        List<Supplier<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> partyService.join(partyId, guest.getId()));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(successCount(results)).isEqualTo(1);
        assertThat(count(results, ErrorCode.ALREADY_JOINED)).isEqualTo(9);
        assertThat(partyMemberRepository.findJoinedMemberIdsByPartyId(partyId))
                .filteredOn(id -> id.equals(guest.getId())).hasSize(1);
        // 호스트 1 + guest 1 → 남은 자리 3
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("3");
    }
}
