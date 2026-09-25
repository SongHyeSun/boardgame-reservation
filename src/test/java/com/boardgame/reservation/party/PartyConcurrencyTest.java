package com.boardgame.reservation.party;

import com.boardgame.reservation.boardgame.domain.BoardGame;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.member.domain.Member;
import com.boardgame.reservation.party.dto.PartyCreateRequest;
import com.boardgame.reservation.party.service.PartyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 정합성 검증: 서비스를 직접, 진짜 동시에 호출한다 (트랜잭션 없는 테스트 → 각 호출이 독립 커밋).
 * 실제 Redis(Testcontainers) + H2.
 */
class PartyConcurrencyTest extends PartyRedisTestSupport {

    private static final int THREADS = 32;

    @Autowired
    PartyService partyService;

    /** 모든 작업을 latch 로 동시에 출발시키고, 결과를 ErrorCode(성공이면 null)로 모아 돌려준다 */
    private static List<ErrorCode> runConcurrently(List<Supplier<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        // 풀 크기보다 작업이 많으면 동시에 대기 가능한 건 THREADS 개뿐 → 그만큼만 기다렸다가 출발
        CountDownLatch ready = new CountDownLatch(Math.min(tasks.size(), THREADS));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (Supplier<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        task.get();
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS)); // 예상 밖 예외는 여기서 테스트 실패
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<ErrorCode> results, ErrorCode code) {
        return results.stream().filter(r -> r == code).count();
    }

    @Test
    @DisplayName("capacity 5(호스트 포함) 파티에 100명이 동시에 join → 성공 정확히 4, PARTY_FULL 96, DB 5명, Redis remaining 0")
    void join_100Members_capacity5() throws Exception {
        Member host = saveMember("host");
        BoardGame boardGame = saveBoardGame(2, 8);
        Long partyId = partyService.create(host.getId(),
                new PartyCreateRequest(boardGame.getId(), "선착순", null, 5, null)).id();

        List<Member> members = memberRepository.saveAll(
                IntStream.range(0, 100)
                        .mapToObj(i -> Member.createUser("m" + i + "@test.com", "pw", "m" + i))
                        .toList());
        List<Supplier<?>> tasks = members.stream()
                .<Supplier<?>>map(m -> () -> partyService.join(partyId, m.getId()))
                .toList();

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(results.stream().filter(r -> r == null).count()).isEqualTo(4);
        assertThat(count(results, ErrorCode.PARTY_FULL)).isEqualTo(96);
        assertThat(partyMemberRepository.countByPartyId(partyId)).isEqualTo(5);
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("0");
    }

    @Test
    @DisplayName("같은 회원이 10번 동시 join → 성공 1, ALREADY_JOINED 9, 그 회원의 DB 행 1")
    void join_sameMember_10times() throws Exception {
        Member host = saveMember("host");
        Member guest = saveMember("guest");
        BoardGame boardGame = saveBoardGame(2, 8);
        Long partyId = partyService.create(host.getId(),
                new PartyCreateRequest(boardGame.getId(), "중복 방어", null, 5, null)).id();

        List<Supplier<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> partyService.join(partyId, guest.getId()));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(results.stream().filter(r -> r == null).count()).isEqualTo(1);
        assertThat(count(results, ErrorCode.ALREADY_JOINED)).isEqualTo(9);
        assertThat(partyMemberRepository.findMemberIdsByPartyId(partyId))
                .filteredOn(id -> id.equals(guest.getId())).hasSize(1);
        // 호스트 1 + guest 1 → 남은 자리 3
        assertThat(redisTemplate.opsForValue().get(remainingKey(partyId))).isEqualTo("3");
    }
}
