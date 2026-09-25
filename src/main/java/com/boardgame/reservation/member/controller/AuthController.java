package com.boardgame.reservation.member.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.member.dto.LoginRequest;
import com.boardgame.reservation.member.dto.MemberResponse;
import com.boardgame.reservation.member.dto.SignupRequest;
import com.boardgame.reservation.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/signup  회원가입 (201)
 * POST /api/auth/login   로그인 → 세션 생성 (200)
 * POST /api/auth/logout  → SecurityConfig의 logout 설정이 처리 (컨트롤러 메서드 없음)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MemberService memberService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
        MemberResponse response = memberService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MemberResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 1) 인증: 실패하면 BadCredentialsException → GlobalExceptionHandler에서 401
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        MemberService.normalizeEmail(request.email()), request.password()));

        // 2) 세션 고정 공격 방지: 로그인 전 세션이 있었다면 세션 ID를 새로 발급
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        // 3) SecurityContext 생성 후 세션에 저장 → 이후 요청은 JSESSIONID 쿠키로 인증됨
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(ApiResponse.ok(memberService.getMember(principal.getId())));
    }
}
