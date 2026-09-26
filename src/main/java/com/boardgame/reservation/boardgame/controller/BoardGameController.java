package com.boardgame.reservation.boardgame.controller;

import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.domain.PlayMode;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.dto.BoardGameResponse;
import com.boardgame.reservation.boardgame.dto.BoardGameVisibilityRequest;
import com.boardgame.reservation.boardgame.service.BoardGameService;
import com.boardgame.reservation.global.exception.BusinessException;
import com.boardgame.reservation.global.exception.ErrorCode;
import com.boardgame.reservation.global.response.ApiResponse;
import com.boardgame.reservation.global.security.MemberPrincipal;
import com.boardgame.reservation.member.domain.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * GET    /api/boardgames                   목록 (players, difficulty, keyword, playMode 선택 필터) — 누구나
 *                                          기본은 숨기지 않은 게임만. mine=true 는 내가 등록한 게임(숨김 포함) — ADMIN
 * GET    /api/boardgames/{id}              상세 (숨긴 게임도 visible:false 로 반환) — 누구나
 * POST   /api/boardgames                   등록 (201) multipart: data(JSON) + image(선택) — ADMIN
 * PUT    /api/boardgames/{id}              수정 multipart: data(JSON, removeImage 포함) + image(선택) — 소유 관리자
 * PATCH  /api/boardgames/{id}/visibility   숨기기/다시 보이기 {visible} — 소유 관리자
 * 게임 삭제는 없다(숨기기로 대체). 역할 권한은 SecurityConfig 의 requestMatchers 에서, 소유자 검사는 서비스에서 한다.
 */
@RestController
@RequestMapping("/api/boardgames")
@RequiredArgsConstructor
public class BoardGameController {

    private final BoardGameService boardGameService;

    /** GET 은 permitAll 이라 principal 은 비로그인이면 null. mine=true 일 때만 로그인·관리자 여부를 여기서 확인한다 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BoardGameResponse>>> list(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(required = false) Integer players,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) PlayMode playMode,
            @RequestParam(defaultValue = "false") boolean mine
    ) {
        Long ownerId = mine ? requireAdmin(principal).getId() : null;
        return ResponseEntity.ok(ApiResponse.ok(
                boardGameService.search(players, difficulty, keyword, playMode, ownerId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardGameResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(boardGameService.getBoardGame(id)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BoardGameResponse>> create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestPart("data") BoardGameRequest data,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(boardGameService.create(principal.getId(), data, image)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BoardGameResponse>> update(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestPart("data") BoardGameRequest data,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(ApiResponse.ok(boardGameService.update(id, principal.getId(), data, image)));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<ApiResponse<BoardGameResponse>> changeVisibility(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody BoardGameVisibilityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                boardGameService.changeVisibility(id, principal.getId(), request.visible())));
    }

    private static MemberPrincipal requireAdmin(MemberPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (principal.getRole() == Role.USER) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return principal;
    }
}
