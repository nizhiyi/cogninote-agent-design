package com.itqianchen.agentdesign.domain.chat;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.search.SearchMode;

/**
 * 已持久化的聊天消息。
 *
 * <p>sequence 是同一 conversationId 内的稳定顺序号，长会话摘要和历史裁剪都依赖它。
 * sourcesJson 保存回答产生时的 RAG 来源快照，不能用当前索引结果反推替代。</p>
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
