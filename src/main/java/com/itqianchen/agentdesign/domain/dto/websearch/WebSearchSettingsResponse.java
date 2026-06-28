package com.itqianchen.agentdesign.domain.dto.websearch;

import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;

/**
 * 联网搜索设置响应。
 *
 * <p>该响应只暴露 API Key 是否已配置，不暴露明文密钥。</p>
 */
public record WebSearchSettingsResponse(
        boolean enabled,
        WebSearchProvider provider,
        boolean apiKeyConfigured,
        int maxResults,
        int maxCallsPerTurn,
        int timeoutMs,
        String searchMode
) {
}
