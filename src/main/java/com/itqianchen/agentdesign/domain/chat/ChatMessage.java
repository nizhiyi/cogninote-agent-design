package com.itqianchen.agentdesign.domain.chat;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.search.SearchMode;

/**
 * Chat Message 是 聊天会话 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record ChatMessage(
        String id,
        String conversationId,
        int sequence,
        ChatMessageRole role,
        String content,
        ChatMessageStatus status,
        String requestId,
        AgentType agentType,
        SearchMode retrievalMode,
        String sourcesJson,
        int tokenEstimate,
        long createdAt
) {
}
