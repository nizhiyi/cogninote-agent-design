package com.itqianchen.agentdesign.service.websearch;

import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;

/**
 * 联网搜索运行时设置快照。
 *
 * <p>快照会进入 ToolContext，因此 toString 必须脱敏，避免日志或异常链路泄露 API Key。</p>
 */
public record WebSearchSettingsSnapshot(
        boolean enabled,
        WebSearchProvider provider,
        String apiKey,
        int maxResults,
        int maxCallsPerTurn,
        int timeoutMs,
        String searchMode
) {
    public boolean apiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "WebSearchSettingsSnapshot[enabled=%s, provider=%s, apiKeyConfigured=%s, maxResults=%d, maxCallsPerTurn=%d, timeoutMs=%d, searchMode=%s]"
                .formatted(enabled, provider, apiKeyConfigured(), maxResults, maxCallsPerTurn, timeoutMs, searchMode);
    }
}
