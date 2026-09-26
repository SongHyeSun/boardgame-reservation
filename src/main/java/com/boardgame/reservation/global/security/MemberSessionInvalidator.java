package com.boardgame.reservation.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/**
 * 회원의 로그인 세션을 전부 무효화한다 (재로그인 유도).
 *
 * 필요한 이유: 세션(Redis)에 저장된 권한은 로그인 시점 값이라, 관리자 승인으로 role 이 바뀌어도
 *   기존 세션은 예전 권한(USER) 그대로다.
 * 동작 조건: spring.session.data.redis.repository-type=indexed (FindByIndexNameSessionRepository 구현).
 *   principal name = Authentication.getName() = 로그인 email(소문자) 이라 email 로 찾는다.
 *   세션 저장소가 없거나(테스트에서 Redis 세션 자동설정을 끈 경우) indexed 가 아니면 경고만 남기고 넘어간다.
 *
 * 호출 시점은 DB 커밋 이후를 권장(TransactionCallbacks.afterCommit) — 롤백됐는데 세션만 끊기는 일을 막는다.
 * 여기서 Redis 예외가 나도 삼킨다: DB 는 이미 커밋됐으므로 API 를 실패시키면 안 되고, 세션은 만료 시간(기본 30분)에 정리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSessionInvalidator {

    private final ObjectProvider<FindByIndexNameSessionRepository<?>> sessionRepositoryProvider;

    public void invalidateAll(String principalName) {
        FindByIndexNameSessionRepository<?> repository = sessionRepositoryProvider.getIfAvailable();
        if (repository == null) {
            log.warn("세션 저장소가 principal 인덱스를 지원하지 않아 세션을 무효화하지 못했습니다 (repository-type=indexed 필요): {}",
                    principalName);
            return;
        }
        try {
            repository.findByPrincipalName(principalName).keySet().forEach(repository::deleteById);
        } catch (RuntimeException e) {
            log.error("세션 무효화 실패 (기존 세션은 만료될 때까지 이전 권한으로 남음): {}", principalName, e);
        }
    }
}
