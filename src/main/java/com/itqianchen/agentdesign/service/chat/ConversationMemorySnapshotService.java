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
 * 按当前模型上下文窗口生成会话记忆快照。
 *
 * <p>快照只选取 summaryMessageSequence 之后的消息，并从最近消息向前填充预算，保证最新对话优先进入模型。</p>
 */
@Service
public class ConversationMemorySnapshotService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryProperties memoryProperties;
    private final TokenEstimator tokenEstimator;
    private final ModelConfigService modelConfigService;

    /**
     * 注入会话、记忆配置、token 估算和模型配置依赖。
     *
     * @param chatSessionRepository 会话仓储
     * @param memoryProperties 记忆窗口配置
     * @param tokenEstimator token 估算器
     * @param modelConfigService 模型配置服务
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
     * 生成会话完整记忆快照。
     *
     * @param conversationId 会话 ID
     * @return 记忆快照
     */
    public ConversationMemorySnapshot snapshot(String conversationId) {
        return snapshot(conversationId, Integer.MAX_VALUE);
    }

    /**
     * 生成截至指定 sequence 的记忆快照。
     *
     * <p>用于模型调用前截取当时可见历史，避免正在追加的消息提前进入上下文。</p>
     *
     * @param conversationId 会话 ID
     * @param maxSequenceInclusive 最大可见消息序号
     * @return 记忆快照
     */
    public ConversationMemorySnapshot snapshot(String conversationId, int maxSequenceInclusive) {
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        ChatSession session = chatSessionRepository.findById(conversationId).orElse(null);
        if (session == null) {
            return emptySnapshot(chatConfig);
        }
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
                chatSessionRepository.countMessages(conversationId),
                chatConfig.resolvedContextWindowTokens(),
                historyBudgetTokens(chatConfig),
                estimationMethod(session.summary(), selected, chatConfig)
        );
    }

    /**
     * 按预算从最近消息向前选择历史。
     *
     * @param messages 候选消息
     * @param chatConfig 当前 Chat 配置
     * @return 预算内的最近消息
     */
    private List<ChatMessage> selectByBudget(List<ChatMessage> messages, ModelConfig chatConfig) {
        if (messages.isEmpty()) {
            return List.of();
        }

        int minimum = memoryProperties.resolvedMinimumRecentMessages();
        int budget = historyBudgetTokens(chatConfig);
        List<ChatMessage> selected = new ArrayList<>();
        int tokens = 0;

        // 从最新消息向前取，既保留最近上下文，也允许 minimumRecentMessages 突破预算兜住短对话连贯性。
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
     * 计算历史消息可使用的 token 预算。
     *
     * <p>默认使用上下文窗口的 80%，为系统提示词、用户新问题和检索上下文保留空间。</p>
     *
     * @param chatConfig 当前 Chat 配置
     * @return 历史消息预算 token
     */
    public int historyBudgetTokens(ModelConfig chatConfig) {
        int contextWindowTokens = chatConfig == null ? 0 : chatConfig.resolvedContextWindowTokens();
        if (contextWindowTokens <= 0) {
            return memoryProperties.resolvedMaxHistoryTokens();
        }
        return Math.max(256, (int) Math.floor(contextWindowTokens * 0.8));
    }

    /**
     * 估算消息列表 token。
     *
     * @param messages 消息列表
     * @param chatConfig 当前 Chat 配置
     * @return 估算 token 总数
     */
    public int estimateMessageTokens(List<ChatMessage> messages, ModelConfig chatConfig) {
        int tokens = 0;
        for (ChatMessage message : messages) {
            tokens += estimateMessageTokens(message, chatConfig);
        }
        return tokens;
    }

    /**
     * 估算单条消息 token。
     *
     * @param message 消息领域对象
     * @param chatConfig 当前 Chat 配置
     * @return 至少为 1 的估算 token 数
     */
    public int estimateMessageTokens(ChatMessage message, ModelConfig chatConfig) {
        return Math.max(1, tokenEstimator.estimateChatMessage(message.content(), chatConfig));
    }

    /**
     * 估算摘要 token。
     *
     * @param summary 摘要文本
     * @param chatConfig 当前 Chat 配置
     * @return 摘要 token 数
     */
    public int estimateSummaryTokens(String summary, ModelConfig chatConfig) {
        return summary == null || summary.isBlank() ? 0 : tokenEstimator.estimateChatMessage(summary, chatConfig);
    }

    /**
     * 构建空会话快照。
     *
     * @param chatConfig 当前 Chat 配置
     * @return 空快照
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
     * 选择快照展示的 token 估算方法。
     *
     * @param summary 摘要文本
     * @param selected 已选消息
     * @param chatConfig 当前 Chat 配置
     * @return 估算方法名称
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
     * 将消息转换为模型记忆条目。
     *
     * @param message 聊天消息
     * @return 记忆条目
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
