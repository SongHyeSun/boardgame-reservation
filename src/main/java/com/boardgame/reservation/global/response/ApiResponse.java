package com.boardgame.reservation.global.response;

/**
 * 공통 응답 포맷 (설계문서 3장)
 * { "success": true, "data": { ... }, "message": null }
 */
public record ApiResponse<T>(boolean success, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
