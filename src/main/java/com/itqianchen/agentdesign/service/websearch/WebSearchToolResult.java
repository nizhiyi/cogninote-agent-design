package com.itqianchen.agentdesign.service.websearch;

import java.util.List;

/**
 * 联网搜索工具返回给模型的结果。
 *
 * <p>必须保持 Jackson 可序列化，不返回 Flux/Mono/Optional 或 provider SDK 原始对象。</p>
 */
public record WebSearchToolResult(
        boolean success,
        String message,
        List<WebSearchResultItem> results
) {
    public static WebSearchToolResult success(List<WebSearchResultItem> results) {
        return new WebSearchToolResult(true, "OK", results == null ? List.of() : List.copyOf(results));
    }

    public static WebSearchToolResult failure(String message) {
        return new WebSearchToolResult(false, message, List.of());
    }
}
