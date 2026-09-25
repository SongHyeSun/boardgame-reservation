package com.boardgame.reservation.global.exception;

import com.boardgame.reservation.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 예외를 공통 응답 포맷(ApiResponse)으로 변환한다.
 *
 * Spring Security 필터에서 발생한 401/403도 SecurityConfig의 EntryPoint/AccessDeniedHandler가
 * 이 클래스로 위임하기 때문에, 응답 형식이 한 곳에서 관리된다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode(), e.getMessage());
    }

    /** @Valid 검증 실패 → 400, 첫 번째 필드 에러 메시지를 내려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return toResponse(ErrorCode.INVALID_INPUT, message);
    }

    /** JSON 형식 오류, enum 값 오타 등 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return toResponse(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    /** 쿼리/경로 파라미터 타입 오류 (?difficulty=FOO, ?players=abc, /boardgames/abc) → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return toResponse(ErrorCode.INVALID_INPUT, e.getName() + ": " + ErrorCode.INVALID_INPUT.getMessage());
    }

    /** 로그인 실패 (이메일 없음 / 비밀번호 틀림 모두 동일 메시지 → 계정 존재 여부 노출 방지) */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException e) {
        return toResponse(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.getMessage());
    }

    /** 인증 안 된 상태로 보호된 API 접근 → 401 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException e) {
        return toResponse(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage());
    }

    /** 로그인은 했지만 권한 부족 (USER가 ADMIN API 호출 등) → 403 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return toResponse(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.fail(message));
    }
}
