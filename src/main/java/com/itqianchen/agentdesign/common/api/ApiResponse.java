package com.itqianchen.agentdesign.common.api;

/**
 * Api 响应 定义返回给前端的 通用 API 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        long timestamp
) {
    /**
     * 创建成功响应包装。
     * <p>统一的响应结构便于前端按固定格式处理接口结果。</p>
     */
    public static <T> ApiResponse<T> ok(T data) {
        // 统一普通 JSON API 的外层结构；SSE 和 204 响应不走这个包装。
        return new ApiResponse<>(true, ApiErrorCode.OK.name(), "OK", data, System.currentTimeMillis());
    }

    /**
     * 创建错误响应包装。
     * <p>错误码和提示文本在这里进入统一 API 契约。</p>
     */
    public static ApiResponse<Void> error(ApiErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message, null, System.currentTimeMillis());
    }
}
