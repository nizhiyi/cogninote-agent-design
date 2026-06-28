package com.itqianchen.agentdesign.service.websearch;

/**
 * Provider 无关的联网搜索请求。
 *
 * <p>Agent 层只关心查询、数量和模式，不依赖具体 provider 的原始请求结构。</p>
 */
public record WebSearchRequest(
        String query,
        int maxResults,
        String searchMode,
        boolean includeHighlights
) {
}
