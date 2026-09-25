package com.boardgame.reservation.boardgame.controller;

import com.boardgame.reservation.boardgame.domain.Difficulty;
import com.boardgame.reservation.boardgame.dto.BoardGameRequest;
import com.boardgame.reservation.boardgame.dto.BoardGameResponse;
import com.boardgame.reservation.boardgame.service.BoardGameService;
import com.boardgame.reservation.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET    /api/boardgames        목록 (players, difficulty, keyword 선택 필터) — 누구나
 * GET    /api/boardgames/{id}   상세 — 누구나
 * POST   /api/boardgames        등록 (201) — ADMIN
 * PUT    /api/boardgames/{id}   수정 — ADMIN
 * DELETE /api/boardgames/{id}   삭제 — ADMIN
 * 권한은 SecurityConfig 의 requestMatchers 에서 처리한다.
 */
@RestController
@RequestMapping("/api/boardgames")
@RequiredArgsConstructor
public class BoardGameController {

    private final BoardGameService boardGameService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BoardGameResponse>>> list(
            @RequestParam(required = false) Integer players,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(ApiResponse.ok(boardGameService.search(players, difficulty, keyword)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardGameResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(boardGameService.getBoardGame(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BoardGameResponse>> create(@Valid @RequestBody BoardGameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(boardGameService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BoardGameResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody BoardGameRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(boardGameService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        boardGameService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
