package com.itqianchen.agentdesign.domain.dto.chat;

/**
 * Chat Done 事件 描述 聊天会话 的事件载荷。
 * <p>主要用于 SSE 流、内部路由或状态传递场景。</p>
 */
public record ChatDoneEvent(Object usage, ChatContextUsageResponse contextUsage) {
}


