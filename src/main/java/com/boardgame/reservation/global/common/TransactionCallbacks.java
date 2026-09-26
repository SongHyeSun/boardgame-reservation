package com.boardgame.reservation.global.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 결과에 맞춰 후처리를 실행하는 헬퍼 (파일-DB 정합성용).
 * - afterCommit: 커밋 이후 실행. 트랜잭션이 없으면(단위 테스트 등) 즉시 실행
 * - afterRollback: 롤백됐을 때만 실행. 트랜잭션이 없으면 실행하지 않음(호출부가 직접 catch 로 정리)
 */
public final class TransactionCallbacks {

    private TransactionCallbacks() {
    }

    public static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public static void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }
}
