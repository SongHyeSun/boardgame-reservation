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
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    INVALID_ADMIN_REQUEST(HttpStatus.CONFLICT, "처리할 수 없는 관리자 신청 상태입니다."),
    INVALID_AVATAR(HttpStatus.BAD_REQUEST, "아바타 설정이 올바르지 않습니다."),

    // 파일
    INVALID_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 파일입니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하만 업로드할 수 있습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),

    // 보드게임
    BOARDGAME_NOT_FOUND(HttpStatus.NOT_FOUND, "보드게임을 찾을 수 없습니다."),
    INVALID_PLAYER_RANGE(HttpStatus.BAD_REQUEST, "최소 인원은 최대 인원보다 클 수 없습니다."),
    BOARDGAME_IN_USE(HttpStatus.CONFLICT, "파티가 있는 보드게임은 삭제할 수 없습니다."),

    // 파티
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "파티를 찾을 수 없습니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "모집 인원이 보드게임 인원 범위를 벗어났습니다."),
    PARTY_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 파티가 아닙니다."),
    PARTY_FULL(HttpStatus.CONFLICT, "정원이 마감되었습니다"),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 파티입니다"),
    NOT_JOINED(HttpStatus.BAD_REQUEST, "참여하지 않은 파티입니다."),
    HOST_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "호스트는 파티를 탈퇴할 수 없습니다."),
    NOT_PARTY_HOST(HttpStatus.FORBIDDEN, "파티 호스트만 할 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
