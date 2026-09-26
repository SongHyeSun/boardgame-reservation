package com.boardgame.reservation.global.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 트랜잭션 동기화를 직접 켜고 끄며 afterCommit / afterRollback 의 실행 시점을 검증 (DB 불필요) */
class TransactionCallbacksTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션이 없으면 afterCommit 은 즉시 실행, afterRollback 은 실행하지 않는다")
    void noTransaction() {
        AtomicInteger commit = new AtomicInteger();
        AtomicInteger rollback = new AtomicInteger();

        TransactionCallbacks.afterCommit(commit::incrementAndGet);
        TransactionCallbacks.afterRollback(rollback::incrementAndGet);

        assertThat(commit).hasValue(1);
        assertThat(rollback).hasValue(0);
    }

    @Test
    @DisplayName("트랜잭션 안에서 afterCommit 은 커밋 전에는 실행되지 않고, 커밋되면 실행된다")
    void afterCommit_runsOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger commit = new AtomicInteger();

        TransactionCallbacks.afterCommit(commit::incrementAndGet);
        assertThat(commit).hasValue(0);

        synchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(commit).hasValue(1);
    }

    @Test
    @DisplayName("롤백되면 afterCommit 은 실행되지 않는다 (afterCommit 은 커밋 때만 호출됨)")
    void afterCommit_notRunOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger commit = new AtomicInteger();

        TransactionCallbacks.afterCommit(commit::incrementAndGet);
        synchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(commit).hasValue(0);
    }

    @Test
    @DisplayName("afterRollback 은 롤백일 때만 실행되고, 커밋 완료(STATUS_COMMITTED)에는 실행되지 않는다")
    void afterRollback_runsOnlyOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        AtomicInteger rollback = new AtomicInteger();

        TransactionCallbacks.afterRollback(rollback::incrementAndGet);
        synchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        assertThat(rollback).hasValue(0);

        synchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(rollback).hasValue(1);
    }

    private static List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}
