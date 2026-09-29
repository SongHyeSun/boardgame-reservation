package com.boardgame.reservation.chat.controller;

import com.boardgame.reservation.chat.dto.ChatRequest;
import com.boardgame.reservation.chat.dto.ChatResponse;
import com.boardgame.reservation.chat.dto.ChatUsageResponse;
import com.boardgame.reservation.chat.service.ChatService;
import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/chat/recommend  AI 추천 요청 — 로그인
 * GET  /api/chat/usage      오늘 남은 횟수 — 로그인
 * 인증은 SecurityConfig의 anyRequest().authenticated()가 이미 커버한다.
 */
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/chat/recommend")
    public ResponseEntity<ApiResponse<ChatResponse>> recommend(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ChatRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.recommend(principal.getId(), request)));
    }

    @GetMapping("/api/chat/usage")
    public ResponseEntity<ApiResponse<ChatUsageResponse>> usage(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(chatService.usage(principal.getId())));
    }
}
