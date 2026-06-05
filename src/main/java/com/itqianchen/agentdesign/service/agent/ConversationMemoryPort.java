package com.itqianchen.agentdesign.service.agent;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

public interface ConversationMemoryPort {

    List<Message> loadRecentMessages(String conversationId, int maxMessages);

    void saveUserMessage(String conversationId, String content);

    void saveAssistantMessage(String conversationId, String content);
}
