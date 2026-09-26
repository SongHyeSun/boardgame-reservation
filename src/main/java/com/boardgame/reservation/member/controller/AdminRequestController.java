package com.boardgame.reservation.member.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.member.dto.AdminRequestResponse;
import com.boardgame.reservation.member.service.AdminRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 가입 승인 API — SecurityConfig 에서 /api/admin/admin-requests/** 는 SUPER_ADMIN 만.
 * GET   /api/admin/admin-requests                        PENDING 목록
 * PATCH /api/admin/admin-requests/{memberId}/approve     승인 (role → ADMIN, 대상 회원의 기존 세션 무효화)
 * PATCH /api/admin/admin-requests/{memberId}/reject      거절
 */
@RestController
@RequestMapping("/api/admin/admin-requests")
@RequiredArgsConstructor
public class AdminRequestController {

    private final AdminRequestService adminRequestService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminRequestResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(adminRequestService.getPendingRequests()));
    }

    @PatchMapping("/{memberId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable Long memberId) {
        adminRequestService.approve(memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{memberId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable Long memberId) {
        adminRequestService.reject(memberId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
