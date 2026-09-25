package com.boardgame.reservation.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 에러 코드 모음.
 * 새 도메인(보드게임, 파티)이 생기면 여기에 코드를 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 회원
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

    // 보드게임
    BOARDGAME_NOT_FOUND(HttpStatus.NOT_FOUND, "보드게임을 찾을 수 없습니다."),
    INVALID_PLAYER_RANGE(HttpStatus.BAD_REQUEST, "최소 인원은 최대 인원보다 클 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
