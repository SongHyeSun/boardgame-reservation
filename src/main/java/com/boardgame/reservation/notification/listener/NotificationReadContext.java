package com.boardgame.reservation.notification.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AFTER_COMMIT 리스너 전용 읽기 트랜잭션 실행기.
 * @TransactionalEventListener 가 걸린 메서드 자신에는 @Transactional 을 (REQUIRES_NEW/NOT_SUPPORTED 가 아니면)
 * 붙일 수 없다(RestrictedTransactionalEventListenerFactory, Spring 7). 그래서 지연 로딩 텍스트(닉네임·게임 이름 등)를
 * 읽어야 하는 리스너는 별도 빈인 이 클래스를 통해 읽기 전용 트랜잭션을 연다. action 안에서 부르는
 * NotificationService.notify() 는 REQUIRES_NEW 라 이 트랜잭션을 잠시 중단하고 자기 몫을 독립적으로 커밋한다
 * (읽기와 쓰기를 하나로 합치면 안 된다 — AFTER_COMMIT 에서 REQUIRED/SUPPORTS 로 저장하면 왜 안 되는지는
 * NotificationService.notify() 의 주석 참고).
 * 이렇게 분리해 두면, 커넥션 풀 고갈 등으로 트랜잭션 획득 자체가 실패해도 그 예외는 이 메서드를 "호출하는" 리스너의
 * try/catch 안에서 발생한 일반 예외로 잡힌다 — 리스너 메서드 자신에 @Transactional 을 붙였다면 그 실패는
 * AOP 프록시가 메서드 진입 "전에" 던지므로 메서드 안의 try/catch 를 건너뛰고 원 요청까지 새어 나간다.
 */
@Component
public class NotificationReadContext {

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable action) {
        action.run();
    }
}
