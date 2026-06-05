package com.itqianchen.agentdesign.service.agent;

import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

@Component
public class NoopConversationMemoryPort implements ConversationMemoryPort {

    @Override
    public List<Message> loadRecentMessages(String conversationId, int maxMessages) {
        return List.of();
    }

    @Override
    public void saveUserMessage(String conversationId, String content) {
        // 第十一阶段只定义聊天记忆边界，真正 SQLite 持久化放到第十二阶段。
    }

    @Override
    public void saveAssistantMessage(String conversationId, String content) {
        // 第十二阶段会在这里保存流式输出完成后的 assistant 消息。
    }
}
