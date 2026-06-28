package com.itqianchen.agentdesign.domain.dto.chat;

import java.util.List;

/**
 * Chat 工具事件载荷。
 *
 * <p>用于 SSE tool 事件向前端增量推送本轮工具调用状态和产生的网页来源。</p>
 */
public record ChatToolEvent(
        String requestId,
        String toolName,
        String query,
        String status,
        long durationMs,
        String message,
        List<RagSourceResponse> sources
) {
}
