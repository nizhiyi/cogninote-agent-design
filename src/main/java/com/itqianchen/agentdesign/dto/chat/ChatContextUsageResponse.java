package com.itqianchen.agentdesign.dto.chat;

/**
 * Chat Context Usage 响应 定义返回给前端的 聊天会话 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record ChatContextUsageResponse(
        int contextWindowTokens,
        int usedTokens,
        int availableTokens,
        double usageRatio,
        boolean compressed,
        int summaryTokens,
        int recentMessageTokens,
        int recentMessageCount,
        int totalMessageCount,
        int summaryMessageSequence,
        String estimationMethod
) {
}
