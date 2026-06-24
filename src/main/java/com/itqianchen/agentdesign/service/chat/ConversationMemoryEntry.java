package com.itqianchen.agentdesign.service.chat;


import com.itqianchen.agentdesign.domain.enums.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.enums.agent.AgentType;
import com.itqianchen.agentdesign.domain.enums.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.enums.search.SearchMode;

/**
 * 注入模型上下文的历史消息条目。
 *
 * <p>agentType 和 retrievalMode 用来区分不同 Agent 模式的历史回答，避免知识库模式的规则污染普通对话。</p>
 */
public record ConversationMemoryEntry(
        AgentType agentType,
        ChatMessageRole role,
        String content,
        SearchMode retrievalMode
) {
}
