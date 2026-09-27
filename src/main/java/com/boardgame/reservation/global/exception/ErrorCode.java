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
    NOT_GAME_OWNER(HttpStatus.FORBIDDEN, "본인이 등록한 게임만 관리할 수 있습니다."),
    INVALID_YOUTUBE_URL(HttpStatus.BAD_REQUEST, "올바른 유튜브 링크가 아닙니다."),
    INVALID_PLAY_MODE(HttpStatus.BAD_REQUEST, "온라인·오프라인 중 하나 이상 선택해야 합니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "오프라인 가능 게임은 재고가 1 이상이어야 합니다."),
    BOARDGAME_NOT_AVAILABLE(HttpStatus.CONFLICT, "운영이 중지된 게임입니다."),
    PLAY_MODE_IN_USE(HttpStatus.CONFLICT, "해당 방식으로 모집 중인 파티가 있어 변경할 수 없습니다."),

    // 파티
    PARTY_NOT_FOUND(HttpStatus.NOT_FOUND, "파티를 찾을 수 없습니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "모집 인원이 허용 범위를 벗어났습니다."),
    INVALID_GAME_SELECTION(HttpStatus.BAD_REQUEST, "보드게임을 선택하거나 게임 이름을 입력해주세요."),
    PLAY_MODE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "이 게임은 해당 방식으로 진행할 수 없습니다."),
    INVALID_ONLINE_LINK(HttpStatus.BAD_REQUEST, "http:// 또는 https:// 링크만 입력할 수 있습니다."),
    PARTY_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 파티가 아닙니다."),
    PARTY_FULL(HttpStatus.CONFLICT, "정원이 마감되었습니다"),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 파티입니다"),
    NOT_JOINED(HttpStatus.BAD_REQUEST, "참여하지 않은 파티입니다."),
    HOST_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "호스트는 파티를 탈퇴할 수 없습니다."),
    NOT_PARTY_HOST(HttpStatus.FORBIDDEN, "파티 호스트만 할 수 있습니다."),
    CANNOT_KICK_HOST(HttpStatus.BAD_REQUEST, "호스트는 내보낼 수 없습니다."),
    KICKED_FROM_PARTY(HttpStatus.FORBIDDEN, "파티장이 내보낸 파티에는 다시 참여할 수 없습니다."),

    // 예약
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
    INVALID_RESERVATION_PERIOD(HttpStatus.BAD_REQUEST, "예약 기간이 올바르지 않습니다."),
    NOT_AVAILABLE(HttpStatus.CONFLICT, "선택한 기간에 대여 가능한 재고가 없습니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "이미 해당 기간에 예약한 게임입니다."),
    INVALID_RESERVATION_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 예약 상태입니다."),
    CANNOT_CANCEL_RESERVATION(HttpStatus.CONFLICT, "취소할 수 없는 예약입니다. 시작일 전날까지만 취소할 수 있습니다."),
    STOCK_BELOW_RESERVED(HttpStatus.CONFLICT, "이미 예약된 수량보다 재고를 줄일 수 없습니다."),
    RESERVATION_BUSY(HttpStatus.CONFLICT, "요청이 몰리고 있습니다. 잠시 후 다시 시도해주세요."),
    RESERVATION_NOT_SUPPORTED(HttpStatus.CONFLICT, "온라인 전용 게임은 대여할 수 없습니다."),

    // 알림
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
