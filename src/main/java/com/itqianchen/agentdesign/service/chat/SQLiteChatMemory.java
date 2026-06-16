package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.chat.ChatMessageStatus;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.repository.chat.ChatSessionRepository;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/**
 * Spring AI ChatMemory 的 SQLite 实现。
 *
 * <p>该适配器只保存 USER/ASSISTANT 消息，系统消息由各 Agent 每次调用时重新注入；
 * 这样切换 Agent 或配置后不会把旧系统规则永久写进会话历史。</p>
 */
@Component
public class SQLiteChatMemory implements ChatMemory {

    private final ChatSessionRepository chatSessionRepository;
    private final TokenEstimator tokenEstimator;
    private final ChatReferencesJsonCodec chatReferencesJsonCodec;

    /**
     * 注入会话仓储和 token 估算器。
     *
     * @param chatSessionRepository 会话仓储
     * @param tokenEstimator token 估算器
     * @param chatReferencesJsonCodec 引用片段编解码器
     */
    public SQLiteChatMemory(
            ChatSessionRepository chatSessionRepository,
            TokenEstimator tokenEstimator,
            ChatReferencesJsonCodec chatReferencesJsonCodec
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.tokenEstimator = tokenEstimator;
        this.chatReferencesJsonCodec = chatReferencesJsonCodec;
    }

    /**
     * 批量写入 Spring AI 记忆消息。
     *
     * <p>Spring AI 可能先写 memory 再进入应用会话流程，因此这里会按同一 conversationId 补齐会话。</p>
     *
     * @param conversationId 会话 ID
     * @param messages Spring AI 消息列表
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        // Spring AI 可能先写 memory 再经过应用会话创建流程，这里用同一个 conversationId 补齐 SQLite 会话。
        chatSessionRepository.ensureSession(conversationId, "新对话", true, SearchMode.HYBRID, 8, now);
        for (Message message : messages) {
            add(conversationId, message);
        }
    }

    /**
     * 读取 Spring AI 可消费的聊天历史。
     *
     * <p>只返回 USER/ASSISTANT，系统消息由 Agent 本轮重新注入。</p>
     *
     * @param conversationId 会话 ID
     * @return Spring AI 消息列表
     */
    @Override
    public List<Message> get(String conversationId) {
        return chatSessionRepository.findMessages(conversationId).stream()
                .filter(message -> message.role() == ChatMessageRole.USER || message.role() == ChatMessageRole.ASSISTANT)
                .map(this::toSpringMessage)
                .toList();
    }

    /**
     * 清空会话记忆。
     *
     * @param conversationId 会话 ID
     */
    @Override
    public void clear(String conversationId) {
        chatSessionRepository.clearMessages(conversationId, System.currentTimeMillis());
    }

    /**
     * 写入单条 Spring AI 消息。
     *
     * @param conversationId 会话 ID
     * @param message Spring AI 消息
     */
    @Override
    public void add(String conversationId, Message message) {
        if (message == null || message.getText() == null || message.getText().isBlank()) {
            return;
        }
        ChatMessageRole role = message.getMessageType() == MessageType.ASSISTANT
                ? ChatMessageRole.ASSISTANT
                : ChatMessageRole.USER;
        chatSessionRepository.appendMessage(
                conversationId,
                role,
                message.getText(),
                ChatMessageStatus.DONE,
                null,
                AgentType.GENERAL_CHAT,
                null,
                null,
                null,
                tokenEstimator.estimate(message.getText()),
                System.currentTimeMillis()
        );
    }

    /**
     * 将本地消息转换为 Spring AI 消息。
     *
     * @param message 本地聊天消息
     * @return Spring AI 消息
     */
    private Message toSpringMessage(ChatMessage message) {
        if (message.role() == ChatMessageRole.ASSISTANT) {
            return new AssistantMessage(message.content());
        }
        return new UserMessage(ChatReferencePromptFormatter.formatUserContent(
                message.content(),
                chatReferencesJsonCodec.decode(message.referencesJson())
        ));
    }
}
