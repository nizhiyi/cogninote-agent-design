package com.itqianchen.agentdesign.domain.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.query-contextualizer")
public record QueryContextualizerProperties(
        Boolean enabled,
        int maxHistoryMessages,
        int maxRewrittenQueryLength
) {

    private static final int DEFAULT_MAX_HISTORY_MESSAGES = 6;
    private static final int DEFAULT_MAX_REWRITTEN_QUERY_LENGTH = 800;

    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }

    public int resolvedMaxHistoryMessages() {
        return Math.max(0, maxHistoryMessages > 0 ? maxHistoryMessages : DEFAULT_MAX_HISTORY_MESSAGES);
    }

    public int resolvedMaxRewrittenQueryLength() {
        return Math.max(100, maxRewrittenQueryLength > 0
                ? maxRewrittenQueryLength
                : DEFAULT_MAX_REWRITTEN_QUERY_LENGTH);
    }
}
