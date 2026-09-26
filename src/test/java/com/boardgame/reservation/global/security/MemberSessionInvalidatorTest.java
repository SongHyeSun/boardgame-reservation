package com.boardgame.reservation.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 세션 저장소를 목으로 대체해 무효화 로직과 실패 격리를 검증 (실제 Redis 를 쓰는 검증은 AdminRequestIntegrationTest) */
@SuppressWarnings({"unchecked", "rawtypes"})
class MemberSessionInvalidatorTest {

    private final ObjectProvider<FindByIndexNameSessionRepository<?>> provider = mock(ObjectProvider.class);
    private final FindByIndexNameSessionRepository repository = mock(FindByIndexNameSessionRepository.class);
    private final MemberSessionInvalidator invalidator = new MemberSessionInvalidator(provider);

    @Test
    @DisplayName("principal(email) 로 찾은 세션을 전부 삭제한다")
    void deletesAllSessionsOfPrincipal() {
        Map<String, Session> sessions = new LinkedHashMap<>();
        sessions.put("session-1", mock(Session.class));
        sessions.put("session-2", mock(Session.class));
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findByPrincipalName("user@test.com")).thenReturn(sessions);

        invalidator.invalidateAll("user@test.com");

        verify(repository).deleteById("session-1");
        verify(repository).deleteById("session-2");
    }

    @Test
    @DisplayName("세션이 없으면 아무것도 삭제하지 않는다")
    void noSessions() {
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findByPrincipalName("user@test.com")).thenReturn(Map.of());

        invalidator.invalidateAll("user@test.com");

        verify(repository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("principal 인덱스를 지원하는 세션 저장소가 없으면 예외 없이 넘어간다")
    void noRepository() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> invalidator.invalidateAll("user@test.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 오류가 나도 예외를 밖으로 던지지 않는다 (DB 는 이미 커밋된 상태)")
    void repositoryFailureIsSwallowed() {
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findByPrincipalName("user@test.com")).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> invalidator.invalidateAll("user@test.com")).doesNotThrowAnyException();
    }
}
