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
 * 计算聊天上下文窗口的前端展示用量。
 *
 * <p>这里复用 ConversationMemorySnapshotService 的预算和 token 估算口径，避免 UI 展示的可用上下文
 * 与真实注入模型的历史窗口不一致。</p>
 */
@Service
public class ChatContextUsageService {

    private final ChatSessionRepository chatSessionRepository;
    private final ConversationMemorySnapshotService memorySnapshotService;
    private final ModelConfigService modelConfigService;
    private final ChatMemoryProperties memoryProperties;

    /**
     * 注入上下文用量计算依赖。
     *
     * @param chatSessionRepository 会话仓储
     * @param memorySnapshotService 记忆快照服务
     * @param modelConfigService 模型配置服务
     * @param memoryProperties 记忆窗口配置
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
     * 计算会话当前上下文用量。
     *
     * <p>会话不存在时返回空用量，便于前端在新会话创建前展示当前模型窗口。</p>
     *
     * @param conversationId 会话 ID
     * @return 上下文用量响应
     */
    public ChatContextUsageResponse usage(String conversationId) {
        ChatSession session = chatSessionRepository.findById(conversationId).orElse(null);
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        if (session == null) {
            return emptyUsage(chatConfig);
        }
        ConversationMemorySnapshot snapshot = memorySnapshotService.snapshot(conversationId);
        return fromSnapshot(session, snapshot);
    }

    /**
     * 从记忆快照构建上下文用量响应。
     *
     * @param session 会话领域对象
     * @param snapshot 当前记忆快照
     * @return 上下文用量响应
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
     * 判断是否需要刷新滚动摘要。
     *
     * <p>触发条件同时考虑消息数和 token 预算；已有摘要后再次超预算也会继续推进摘要边界。</p>
     *
     * @param session 会话领域对象
     * @param messages 当前全量消息
     * @return 是否应该生成新摘要
     */
    public boolean shouldSummarize(ChatSession session, List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return false;
        }
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        int totalTokens = memorySnapshotService.estimateMessageTokens(messages, chatConfig);
        int budgetTokens = memorySnapshotService.historyBudgetTokens(chatConfig);
        // 已有摘要的会话只要新增原文再次超过预算就继续压缩，避免摘要边界停在旧消息上。
        return messages.size() > memoryProperties.resolvedSummarizeAfterMessages()
                || totalTokens > budgetTokens
                || (session.summaryMessageSequence() > 0 && totalTokens > budgetTokens);
    }

    /**
     * 估算单条消息 token。
     *
     * @param message 消息领域对象
     * @return 估算 token 数
     */
    public int estimateMessageTokens(ChatMessage message) {
        return memorySnapshotService.estimateMessageTokens(message, modelConfigService.activeChatOrDefault());
    }

    /**
     * 估算多条消息 token。
     *
     * @param messages 消息列表
     * @return 估算 token 总数
     */
    public int estimateMessagesTokens(List<ChatMessage> messages) {
        return memorySnapshotService.estimateMessageTokens(messages, modelConfigService.activeChatOrDefault());
    }

    /**
     * 构建空会话的上下文用量。
     *
     * @param chatConfig 当前 Chat 配置
     * @return 空用量响应
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
