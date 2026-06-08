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
 * SQLite Chat Memory 承担 聊天会话 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@Component
public class SQLiteChatMemory implements ChatMemory {

    private final ChatSessionRepository chatSessionRepository;
    private final TokenEstimator tokenEstimator;

    /**
     * 注入 SQLiteChatMemory 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SQLiteChatMemory(ChatSessionRepository chatSessionRepository, TokenEstimator tokenEstimator) {
        this.chatSessionRepository = chatSessionRepository;
        this.tokenEstimator = tokenEstimator;
    }

    /**
     * 执行 聊天会话 中的 add 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.ensureSession(conversationId, "新对话", true, SearchMode.HYBRID, 8, now);
        for (Message message : messages) {
            /**
             * 执行 聊天会话 中的 add 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            add(conversationId, message);
        }
    }

    /**
     * 读取 get 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    @Override
    public List<Message> get(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return chatSessionRepository.findMessages(conversationId).stream()
                .filter(message -> message.role() == ChatMessageRole.USER || message.role() == ChatMessageRole.ASSISTANT)
                .map(SQLiteChatMemory::toSpringMessage)
                .toList();
    }

    /**
     * 清理 clear 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @Override
    public void clear(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.clearMessages(conversationId, System.currentTimeMillis());
    }

    /**
     * 执行 聊天会话 中的 add 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public void add(String conversationId, Message message) {
        if (message == null || message.getText() == null || message.getText().isBlank()) {
            return;
        }
        ChatMessageRole role = message.getMessageType() == MessageType.ASSISTANT
                ? ChatMessageRole.ASSISTANT
                : ChatMessageRole.USER;
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.appendMessage(
                conversationId,
                role,
                message.getText(),
                ChatMessageStatus.DONE,
                null,
                AgentType.GENERAL_CHAT,
                null,
                null,
                tokenEstimator.estimate(message.getText()),
                System.currentTimeMillis()
        );
    }

    /**
     * 执行 聊天会话 中的 to Spring Message 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static Message toSpringMessage(ChatMessage message) {
        if (message.role() == ChatMessageRole.ASSISTANT) {
            return new AssistantMessage(message.content());
        }
        return new UserMessage(message.content());
    }
}
