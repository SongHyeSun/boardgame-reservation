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
}
