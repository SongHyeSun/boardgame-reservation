package com.boardgame.reservation.reservation.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.dto.AdminReservationResponse;
import com.boardgame.reservation.reservation.dto.ReservationRejectRequest;
import com.boardgame.reservation.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET   /api/admin/reservations?status=         내가 등록한 게임의 예약 (기본 PENDING) — ADMIN
 * PATCH /api/admin/reservations/{id}/approve    승인 — 게임 소유 관리자
 * PATCH /api/admin/reservations/{id}/reject     거절 {reason?} — 게임 소유 관리자
 * 역할(ADMIN)은 SecurityConfig, 소유자 검사는 서비스에서 한다 (SUPER_ADMIN 도 남의 게임은 403).
 */
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminReservationResponse>>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) ReservationStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.findForOwner(principal.getId(), status)));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminReservationResponse>> approve(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.approve(principal.getId(), id)));
    }

    /** 본문은 선택: 없거나 {} 면 사유 없이 거절 */
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminReservationResponse>> reject(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReservationRejectRequest request
    ) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(reservationService.reject(principal.getId(), id, reason)));
    }
}
