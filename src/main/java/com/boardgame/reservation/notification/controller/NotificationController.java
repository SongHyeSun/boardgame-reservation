package com.boardgame.reservation.notification.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.notification.dto.NotificationResponse;
import com.boardgame.reservation.notification.dto.UnreadCountResponse;
import com.boardgame.reservation.notification.service.NotificationService;
import com.boardgame.reservation.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * GET   /api/notifications/stream     SSE 연결 — 로그인
 * GET   /api/notifications            내 알림 최신순 (page, size)
 * GET   /api/notifications/unread-count
 * PATCH /api/notifications/{id}/read  본인 것만 (아니면 404)
 * PATCH /api/notifications/read-all
 * 인증은 SecurityConfig 의 anyRequest().authenticated() 가 이미 커버한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRepository emitterRepository;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal MemberPrincipal principal) {
        SseEmitter emitter = emitterRepository.connect(principal.getId());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            // emitter 자신의 onError 콜백이 저장소에서 이미 제거한다
            log.debug("SSE 연결 직후 전송 실패: memberId={}", principal.getId(), e);
        }
        return emitter;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.findMine(principal.getId(), pageable)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(
            @AuthenticationPrincipal MemberPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCountResponse(notificationService.countUnread(principal.getId()))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id
    ) {
        notificationService.markRead(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(@AuthenticationPrincipal MemberPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
