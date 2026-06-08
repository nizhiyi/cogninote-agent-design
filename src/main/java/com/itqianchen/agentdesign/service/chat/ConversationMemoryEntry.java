package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.search.SearchMode;

/**
 * Conversation Memory Entry 是 聊天会话 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record ConversationMemoryEntry(
        AgentType agentType,
        ChatMessageRole role,
        String content,
        SearchMode retrievalMode
) {
}
