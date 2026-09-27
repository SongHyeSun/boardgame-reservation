package com.boardgame.reservation.support;

import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** 동시성 테스트 공통: 서비스를 트랜잭션 없이, 진짜 동시에 호출하고 결과를 ErrorCode(성공이면 null)로 모은다 */
public final class ConcurrencyTestUtils {

    public static final int THREADS = 32;

    private ConcurrencyTestUtils() {
    }

    /** 모든 작업을 latch 로 동시에 출발시키고, 결과를 ErrorCode(성공이면 null)로 모아 돌려준다. BusinessException 이 아닌 예외는 테스트 실패 */
    public static List<ErrorCode> runConcurrently(List<Supplier<?>> tasks) throws Exception {
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

    public static long count(List<ErrorCode> results, ErrorCode code) {
        return results.stream().filter(r -> r == code).count();
    }

    public static long successCount(List<ErrorCode> results) {
        return results.stream().filter(r -> r == null).count();
    }
}
