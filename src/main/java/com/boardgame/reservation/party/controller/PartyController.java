package com.boardgame.reservation.party.controller;

import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.party.domain.PartyStatus;
import com.boardgame.reservation.party.dto.JoinResponse;
import com.boardgame.reservation.party.dto.PartyCreateRequest;
import com.boardgame.reservation.party.dto.PartyDetailResponse;
import com.boardgame.reservation.party.dto.PartyResponse;
import com.boardgame.reservation.party.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET    /api/parties               목록 (status, boardGameId 선택 필터) — 누구나
 * GET    /api/parties/{id}          상세 — 누구나
 * POST   /api/parties               개설 (201) — 로그인
 * POST   /api/parties/{id}/join     선착순 참여 {remaining} — 로그인
 * DELETE /api/parties/{id}/leave    참여 취소 — 로그인
 * PATCH  /api/parties/{id}/close    마감 — 호스트
 * 인증은 SecurityConfig(GET permitAll, 나머지 authenticated), 호스트 검사는 서비스에서 한다.
 */
@RestController
@RequestMapping("/api/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PartyResponse>>> list(
            @RequestParam(required = false) PartyStatus status,
            @RequestParam(required = false) Long boardGameId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(partyService.search(status, boardGameId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PartyDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(partyService.getParty(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PartyResponse>> create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody PartyCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(partyService.create(principal.getId(), request)));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<JoinResponse>> join(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(partyService.join(id, principal.getId())));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id
    ) {
        partyService.leave(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{id}/close")
    public ResponseEntity<ApiResponse<Void>> close(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id
    ) {
        partyService.close(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
