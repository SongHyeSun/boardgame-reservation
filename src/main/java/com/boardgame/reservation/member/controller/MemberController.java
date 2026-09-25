package com.boardgame.reservation.member.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.member.dto.MemberResponse;
import com.boardgame.reservation.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /** GET /api/members/me — 로그인 세션 필수 (SecurityConfig: anyRequest().authenticated()) */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> me(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMember(principal.getId())));
    }
}
