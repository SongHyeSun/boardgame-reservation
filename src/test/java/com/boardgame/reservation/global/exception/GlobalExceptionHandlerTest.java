package com.boardgame.reservation.global.exception;

import com.boardgame.reservation.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * multipart·쿼리 파라미터·락 실패 예외 매핑.
 * MockMvc 는 서블릿 컨테이너의 multipart 한도를 타지 않아 MaxUploadSizeExceededException 이 실제로는 안 나오므로
 * 핸들러를 직접 호출해 검증한다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("컨테이너 multipart 한도 초과 → 400 FILE_TOO_LARGE")
    void maxUploadSizeExceeded() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.FILE_TOO_LARGE.getMessage());
    }

    @Test
    @DisplayName("multipart 필수 파트 누락 → 400 INVALID_INPUT")
    void missingPart() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleMissingPart(new MissingServletRequestPartException("data"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).startsWith("data: ");
    }

    @Test
    @DisplayName("multipart 파싱 실패 / 지원하지 않는 Content-Type → 400 INVALID_INPUT")
    void multipartAndMediaType() {
        assertThat(handler.handleMultipart(new MultipartException("broken")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락(availability 의 from/to) → 400 INVALID_INPUT, 파라미터 이름을 알려준다")
    void missingRequestParameter() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("from", "LocalDate"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).startsWith("from: ");
    }

    @Test
    @DisplayName("락 대기 초과(CannotAcquireLockException)·락 실패(PessimisticLockingFailureException) → 409 RESERVATION_BUSY")
    void lockFailure_isBusy() {
        for (PessimisticLockingFailureException exception : List.of(
                new CannotAcquireLockException("lock timeout"),
                new PessimisticLockingFailureException("pessimistic lock failed"))) {
            ResponseEntity<ApiResponse<Void>> response = handler.handleLockFailure(exception);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().success()).isFalse();
            assertThat(response.getBody().message()).isEqualTo(ErrorCode.RESERVATION_BUSY.getMessage());
        }
    }

    @Test
    @DisplayName("BusinessException(ErrorCode, message): 상태코드는 ErrorCode 그대로, 메시지는 넘긴 값")
    void businessException_customMessage() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(
                new BusinessException(ErrorCode.PLAY_MODE_IN_USE, "남은 대여 예약이 있어 오프라인 진행을 끌 수 없습니다."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("남은 대여 예약이 있어 오프라인 진행을 끌 수 없습니다.");
        assertThat(new BusinessException(ErrorCode.PLAY_MODE_IN_USE).getMessage())
                .isEqualTo(ErrorCode.PLAY_MODE_IN_USE.getMessage()); // 기존 생성자는 그대로
    }
}
