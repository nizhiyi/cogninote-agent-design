package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.chat.ChatMemoryProperties;
import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatSession;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.dto.chat.ChatContextUsageResponse;
import com.itqianchen.agentdesign.repository.chat.ChatSessionRepository;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Chat Context Usage 服务 承载 聊天会话 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class ChatContextUsageService {

    private final ChatSessionRepository chatSessionRepository;
    private final ConversationMemorySnapshotService memorySnapshotService;
    private final ModelConfigService modelConfigService;
    private final ChatMemoryProperties memoryProperties;

    /**
     * 注入 ChatContextUsageService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ChatContextUsageService(
            ChatSessionRepository chatSessionRepository,
            ConversationMemorySnapshotService memorySnapshotService,
            ModelConfigService modelConfigService,
            ChatMemoryProperties memoryProperties
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.memorySnapshotService = memorySnapshotService;
        this.modelConfigService = modelConfigService;
        this.memoryProperties = memoryProperties;
    }

    /**
     * 执行 聊天会话 中的 usage 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ChatContextUsageResponse usage(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession session = chatSessionRepository.findById(conversationId).orElse(null);
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        if (session == null) {
            return emptyUsage(chatConfig);
        }
        ConversationMemorySnapshot snapshot = memorySnapshotService.snapshot(conversationId);
        return fromSnapshot(session, snapshot);
    }

    /**
     * 执行 聊天会话 中的 from Snapshot 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ChatContextUsageResponse fromSnapshot(ChatSession session, ConversationMemorySnapshot snapshot) {
        int contextWindowTokens = Math.max(1, snapshot.contextWindowTokens());
        int usedTokens = Math.max(0, snapshot.summaryTokens() + snapshot.recentMessageTokens());
        int availableTokens = Math.max(0, contextWindowTokens - usedTokens);
        return new ChatContextUsageResponse(
                contextWindowTokens,
                usedTokens,
                availableTokens,
                Math.min(1.0, usedTokens / (double) contextWindowTokens),
                session.summaryMessageSequence() > 0 && session.summary() != null && !session.summary().isBlank(),
                snapshot.summaryTokens(),
                snapshot.recentMessageTokens(),
                snapshot.recentMessages().size(),
                snapshot.totalMessageCount(),
                session.summaryMessageSequence(),
                snapshot.estimationMethod()
        );
    }

    /**
     * 执行 聊天会话 中的 should Summarize 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public boolean shouldSummarize(ChatSession session, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return false;
        }
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        int totalTokens = memorySnapshotService.estimateMessageTokens(messages, chatConfig);
        int budgetTokens = memorySnapshotService.historyBudgetTokens(chatConfig);
        return messages.size() > memoryProperties.resolvedSummarizeAfterMessages()
                || totalTokens > budgetTokens
                || (session.summaryMessageSequence() > 0 && totalTokens > budgetTokens);
    }

    /**
     * 估算 estimate Message Tokens 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimateMessageTokens(ChatMessage message) {
        return memorySnapshotService.estimateMessageTokens(message, modelConfigService.activeChatOrDefault());
    }

    /**
     * 估算 estimate Messages Tokens 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimateMessagesTokens(List<ChatMessage> messages) {
        return memorySnapshotService.estimateMessageTokens(messages, modelConfigService.activeChatOrDefault());
    }

    /**
     * 执行 聊天会话 中的 empty Usage 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ChatContextUsageResponse emptyUsage(ModelConfig chatConfig) {
        int contextWindowTokens = Math.max(1, chatConfig.resolvedContextWindowTokens());
        return new ChatContextUsageResponse(
                contextWindowTokens,
                0,
                contextWindowTokens,
                0.0,
                false,
                0,
                0,
                0,
                0,
                0,
                "empty"
        );
    }
}
