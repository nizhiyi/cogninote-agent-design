package com.itqianchen.agentdesign.domain.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat Memory 配置属性 映射 聊天会话 的 YAML 配置。
 * <p>通过类型化配置隔离环境变量、默认值和业务代码。</p>
 */
@ConfigurationProperties(prefix = "app.chat.memory")
public record ChatMemoryProperties(
        int maxHistoryTokens,
        int minimumRecentMessages,
        int summarizeAfterMessages
) {

    private static final int DEFAULT_MAX_HISTORY_TOKENS = 6000;
    private static final int DEFAULT_MINIMUM_RECENT_MESSAGES = 8;
    private static final int DEFAULT_SUMMARIZE_AFTER_MESSAGES = 200;

    /**
     * 解析 resolved Max History Tokens 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public int resolvedMaxHistoryTokens() {
        return maxHistoryTokens > 0 ? maxHistoryTokens : DEFAULT_MAX_HISTORY_TOKENS;
    }

    /**
     * 解析 resolved Minimum Recent Messages 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public int resolvedMinimumRecentMessages() {
        return Math.max(2, minimumRecentMessages > 0 ? minimumRecentMessages : DEFAULT_MINIMUM_RECENT_MESSAGES);
    }

    /**
     * 解析 resolved Summarize After Messages 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public int resolvedSummarizeAfterMessages() {
        return Math.max(
                resolvedMinimumRecentMessages() + 2,
                summarizeAfterMessages > 0 ? summarizeAfterMessages : DEFAULT_SUMMARIZE_AFTER_MESSAGES
        );
    }
}
