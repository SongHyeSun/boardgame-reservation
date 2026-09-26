package com.boardgame.reservation.global.exception;

import com.boardgame.reservation.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * multipart 관련 예외 매핑.
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
}
