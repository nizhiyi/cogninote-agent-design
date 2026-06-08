package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.chat.ChatMemoryProperties;
import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatSession;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.repository.chat.ChatSessionRepository;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Conversation Memory Snapshot 服务 承载 聊天会话 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class ConversationMemorySnapshotService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryProperties memoryProperties;
    private final TokenEstimator tokenEstimator;
    private final ModelConfigService modelConfigService;

    /**
     * 注入 ConversationMemorySnapshotService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ConversationMemorySnapshotService(
            ChatSessionRepository chatSessionRepository,
            ChatMemoryProperties memoryProperties,
            TokenEstimator tokenEstimator,
            ModelConfigService modelConfigService
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.memoryProperties = memoryProperties;
        this.tokenEstimator = tokenEstimator;
        this.modelConfigService = modelConfigService;
    }

    /**
     * 执行 聊天会话 中的 snapshot 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ConversationMemorySnapshot snapshot(String conversationId) {
        return snapshot(conversationId, Integer.MAX_VALUE);
    }

    /**
     * 执行 聊天会话 中的 snapshot 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ConversationMemorySnapshot snapshot(String conversationId, int maxSequenceInclusive) {
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession session = chatSessionRepository.findById(conversationId).orElse(null);
        if (session == null) {
            return emptySnapshot(chatConfig);
        }

        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        List<ChatMessage> messages = chatSessionRepository.findMessagesAfter(
                conversationId,
                session.summaryMessageSequence()
        ).stream()
                .filter(message -> message.sequence() <= maxSequenceInclusive)
                .toList();
        List<ChatMessage> selected = selectByBudget(messages, chatConfig);
        int lastSequence = selected.isEmpty() ? session.summaryMessageSequence() : selected.getLast().sequence();
        int summaryTokens = estimateSummaryTokens(session.summary(), chatConfig);
        int recentMessageTokens = estimateMessageTokens(selected, chatConfig);
        return new ConversationMemorySnapshot(
                session.summary(),
                selected.stream().map(ConversationMemorySnapshotService::toMemoryEntry).toList(),
                lastSequence,
                summaryTokens,
                recentMessageTokens,
                // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
                chatSessionRepository.countMessages(conversationId),
                chatConfig.resolvedContextWindowTokens(),
                historyBudgetTokens(chatConfig),
                /**
                 * 执行 聊天会话 中的 estimation Method 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                estimationMethod(session.summary(), selected, chatConfig)
        );
    }

    /**
     * 执行 聊天会话 中的 select By Budget 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<ChatMessage> selectByBudget(List<ChatMessage> messages, ModelConfig chatConfig) {
        if (messages.isEmpty()) {
            return List.of();
        }

        int minimum = memoryProperties.resolvedMinimumRecentMessages();
        int budget = historyBudgetTokens(chatConfig);
        List<ChatMessage> selected = new ArrayList<>();
        int tokens = 0;

        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            int nextTokens = tokens + estimateMessageTokens(message, chatConfig);
            boolean insideMinimumWindow = selected.size() < minimum;
            if (!insideMinimumWindow && nextTokens > budget) {
                break;
            }
            selected.addFirst(message);
            tokens = nextTokens;
        }
        return List.copyOf(selected);
    }

    /**
     * 执行 聊天会话 中的 history Budget Tokens 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public int historyBudgetTokens(ModelConfig chatConfig) {
        int contextWindowTokens = chatConfig == null ? 0 : chatConfig.resolvedContextWindowTokens();
        if (contextWindowTokens <= 0) {
            return memoryProperties.resolvedMaxHistoryTokens();
        }
        return Math.max(256, (int) Math.floor(contextWindowTokens * 0.8));
    }

    /**
     * 估算 estimate Message Tokens 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimateMessageTokens(List<ChatMessage> messages, ModelConfig chatConfig) {
        int tokens = 0;
        for (ChatMessage message : messages) {
            tokens += estimateMessageTokens(message, chatConfig);
        }
        return tokens;
    }

    /**
     * 估算 estimate Message Tokens 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimateMessageTokens(ChatMessage message, ModelConfig chatConfig) {
        return Math.max(1, tokenEstimator.estimateChatMessage(message.content(), chatConfig));
    }

    /**
     * 估算 estimate Summary Tokens 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimateSummaryTokens(String summary, ModelConfig chatConfig) {
        return summary == null || summary.isBlank() ? 0 : tokenEstimator.estimateChatMessage(summary, chatConfig);
    }

    /**
     * 执行 聊天会话 中的 empty Snapshot 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private ConversationMemorySnapshot emptySnapshot(ModelConfig chatConfig) {
        return new ConversationMemorySnapshot(
                null,
                List.of(),
                0,
                0,
                0,
                0,
                chatConfig.resolvedContextWindowTokens(),
                historyBudgetTokens(chatConfig),
                tokenEstimator.estimateWithMethod("context", chatConfig).method()
        );
    }

    /**
     * 执行 聊天会话 中的 estimation Method 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private String estimationMethod(String summary, List<ChatMessage> selected, ModelConfig chatConfig) {
        if (summary != null && !summary.isBlank()) {
            return tokenEstimator.estimateWithMethod(summary, chatConfig).method();
        }
        for (ChatMessage message : selected) {
            if (message.content() != null && !message.content().isBlank()) {
                return tokenEstimator.estimateWithMethod(message.content(), chatConfig).method();
            }
        }
        return tokenEstimator.estimateWithMethod("context", chatConfig).method();
    }

    /**
     * 执行 聊天会话 中的 to Memory Entry 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ConversationMemoryEntry toMemoryEntry(ChatMessage message) {
        return new ConversationMemoryEntry(
                message.agentType(),
                message.role(),
                message.content(),
                message.retrievalMode()
        );
    }
}
