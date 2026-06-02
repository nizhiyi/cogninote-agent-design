package com.itqianchen.agentdesign.common.api;

public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        long timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        // 统一普通 JSON API 的外层结构；SSE 和 204 响应不走这个包装。
        return new ApiResponse<>(true, ApiErrorCode.OK.name(), "OK", data, System.currentTimeMillis());
    }

    public static ApiResponse<Void> error(ApiErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message, null, System.currentTimeMillis());
    }
}
