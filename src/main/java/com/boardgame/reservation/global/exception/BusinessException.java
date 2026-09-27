package com.boardgame.reservation.global.exception;

import lombok.Getter;

/**
 * 서비스 계층에서 던지는 예외. GlobalExceptionHandler가 ErrorCode의 상태코드/메시지로 변환한다.
 * 예) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 상태코드는 ErrorCode 그대로, 메시지만 상황에 맞게 바꿀 때 (예: 같은 PLAY_MODE_IN_USE 를 예약 때문에 던질 때) */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
