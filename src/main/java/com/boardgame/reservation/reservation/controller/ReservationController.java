package com.boardgame.reservation.reservation.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.reservation.domain.ReservationStatus;
import com.boardgame.reservation.reservation.dto.AvailabilityResponse;
import com.boardgame.reservation.reservation.dto.ReservationCreateRequest;
import com.boardgame.reservation.reservation.dto.ReservationResponse;
import com.boardgame.reservation.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * GET   /api/boardgames/{id}/availability?from=&to=   날짜별 남은 수량 — 누구나 (SecurityConfig 의 GET /api/boardgames/** permitAll)
 * POST  /api/reservations                             대여 신청 (201) — 로그인
 * GET   /api/reservations/me?status=                  내 예약 최신순 — 로그인
 * PATCH /api/reservations/{id}/cancel                 본인 취소 — 로그인
 * 날짜는 ISO(yyyy-MM-dd). 인증은 SecurityConfig, 본인 확인은 서비스에서 한다.
 */
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/api/boardgames/{id}/availability")
    public ResponseEntity<ApiResponse<List<AvailabilityResponse>>> availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.getAvailability(id, from, to)));
    }

    @PostMapping("/api/reservations")
    public ResponseEntity<ApiResponse<ReservationResponse>> create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(reservationService.create(principal.getId(), request)));
    }

    @GetMapping("/api/reservations/me")
    public ResponseEntity<ApiResponse<List<ReservationResponse>>> mine(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) ReservationStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.findMine(principal.getId(), status)));
    }

    @PatchMapping("/api/reservations/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancel(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(reservationService.cancel(principal.getId(), id)));
    }
}
