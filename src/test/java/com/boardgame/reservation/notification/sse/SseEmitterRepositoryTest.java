package com.boardgame.reservation.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 스프링 컨텍스트 없이 저장소 자체의 등록/제거/전송 동작만 검증한다 (실제 비동기 서블릿 응답은 없음). */
class SseEmitterRepositoryTest {

    private final SseEmitterRepository repository = new SseEmitterRepository(SseEmitterRepository.DEFAULT_TIMEOUT_SECONDS);

    @SuppressWarnings("unchecked")
    private Map<Long, List<SseEmitter>> emitters() {
        return (Map<Long, List<SseEmitter>>) ReflectionTestUtils.getField(repository, "emitters");
    }

    @Test
    @DisplayName("emitter 타임아웃은 설정값(초)을 따른다 — 기본 30분, prod 110초")
    void connect_usesConfiguredTimeout() {
        assertThat(repository.connect(1L).getTimeout()).isEqualTo(30 * 60 * 1000L);
        assertThat(new SseEmitterRepository(110).connect(2L).getTimeout()).isEqualTo(110_000L);
    }

    @Test
    @DisplayName("connect 하면 그 회원 id 로 emitter 가 등록된다")
    void connect_registersEmitterForMember() {
        SseEmitter emitter = repository.connect(1L);

        assertThat(emitter).isNotNull();
        assertThat(emitters().get(1L)).containsExactly(emitter);
    }

    @Test
    @DisplayName("같은 회원이 여러 번 connect 하면(탭 여러 개) 모두 유지된다")
    void connect_multipleTabsForSameMember_keepsAll() {
        SseEmitter first = repository.connect(1L);
        SseEmitter second = repository.connect(1L);

        assertThat(emitters().get(1L)).containsExactly(first, second);
    }

    // onCompletion/onTimeout/onError 콜백은 ResponseBodyEmitter.Handler 가 실제로 initialize() 된 뒤에만
    // (Spring MVC 가 진짜 비동기 서블릿 요청을 처리할 때) 실행된다 — Handler/initialize() 모두 패키지 전용이라
    // 이 순수 단위 테스트에서는 재현할 수 없다. 등록/전송 동작만 여기서 검증하고, 실제 연결-해제는 SSE 통합 확인으로 갈음한다.

    @Test
    @DisplayName("연결되지 않은 회원에게 send 해도 예외 없이 아무 일도 하지 않는다")
    void send_toUnknownMember_doesNothing() {
        assertThatCode(() -> repository.send(999L, "notification", "payload")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연결된 회원에게 send 하면 예외 없이 전송을 시도한다")
    void send_toConnectedMember_doesNotThrow() {
        repository.connect(1L);

        assertThatCode(() -> repository.send(1L, "notification", "payload")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연결이 없어도 heartbeat 는 예외 없이 끝난다")
    void heartbeat_withNoConnections_doesNotThrow() {
        assertThatCode(repository::heartbeat).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("연결된 emitter 가 있어도 heartbeat 는 예외 없이 comment 를 보낸다")
    void heartbeat_withConnections_doesNotThrow() {
        repository.connect(1L);
        repository.connect(2L);

        assertThatCode(repository::heartbeat).doesNotThrowAnyException();
    }
}
