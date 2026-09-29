package com.boardgame.reservation.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * memberId → 연결된 SseEmitter 목록(탭 여러 개 허용). 회원 id 는 연결 시점에 컨트롤러 스레드(@AuthenticationPrincipal)에서
 * 미리 꺼내 키로 쓴다 — heartbeat 스케줄러·Redis 구독 콜백 스레드는 SecurityContextHolder(기본 MODE_THREADLOCAL)에 접근할 수 없다.
 * onCompletion/onTimeout/onError 는 저장소에서 제거만 하고 절대 재던지지 않는다 — 응답이 이미 커밋된 뒤 예외를 다시 던지면
 * (예: async dispatch 재검사 중 세션 무효화) 쓸 곳 없는 에러 바디를 쓰려다 실패할 뿐이다.
 */
@Slf4j
@Component
public class SseEmitterRepository {

    /** app.notification.sse-timeout-seconds 기본값(로컬). prod 는 Vercel 프록시 120초보다 짧게(110초) 줘서 서버가 먼저 닫는다 */
    static final long DEFAULT_TIMEOUT_SECONDS = 1800; // 30분 (@Value 기본값에 쓰이므로 컴파일 타임 상수여야 함)

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final long timeoutMs;

    public SseEmitterRepository(
            @Value("${app.notification.sse-timeout-seconds:" + DEFAULT_TIMEOUT_SECONDS + "}") long timeoutSeconds) {
        this.timeoutMs = Duration.ofSeconds(timeoutSeconds).toMillis();
    }

    public SseEmitter connect(Long memberId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        emitters.computeIfAbsent(memberId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(() -> remove(memberId, emitter));
        emitter.onError(e -> remove(memberId, emitter));
        return emitter;
    }

    private void remove(Long memberId, SseEmitter emitter) {
        emitters.computeIfPresent(memberId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    public void send(Long memberId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(memberId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(list)) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                remove(memberId, emitter);
            }
        }
    }

    /** 25초마다 comment 이벤트로 연결을 유지한다 (프록시·브라우저가 idle 연결을 끊지 않도록) */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((memberId, list) -> {
            for (SseEmitter emitter : List.copyOf(list)) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    remove(memberId, emitter);
                }
            }
        });
    }
}
