package com.itqianchen.agentdesign.repository.chat;

import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.chat.ChatMessageStatus;
import com.itqianchen.agentdesign.domain.chat.ChatSession;
import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.ChatSessionResponse;
import com.itqianchen.agentdesign.mapper.chat.ChatSessionMapper;
import com.itqianchen.agentdesign.mapper.chat.ChatSessionSummaryRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Chat Session 仓储 是 聊天会话 的持久化边界。
 * <p>服务层通过该类型访问数据，避免直接依赖 MyBatis Mapper 细节。</p>
 */
@Repository
public class ChatSessionRepository {

    private final ChatSessionMapper chatSessionMapper;

    /**
     * 注入 ChatSessionRepository 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ChatSessionRepository(ChatSessionMapper chatSessionMapper) {
        this.chatSessionMapper = chatSessionMapper;
    }

    /**
     * 读取 find Active Sessions 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public List<ChatSession> findActiveSessions() {
        return chatSessionMapper.findActiveSessions();
    }

    /**
     * 读取 find Active Session Summaries 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public List<ChatSessionResponse> findActiveSessionSummaries() {
        return chatSessionMapper.findActiveSessionSummaries().stream()
                .map(ChatSessionRepository::toSummaryResponse)
                .toList();
    }

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<ChatSession> findById(String id) {
        return chatSessionMapper.findById(id).stream().findFirst();
    }

    /**
     * 创建 create 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    public ChatSession create(String title, boolean useKnowledgeBase, SearchMode mode, int topK, long now) {
        return create(UUID.randomUUID().toString(), title, useKnowledgeBase, mode, topK, now);
    }

    /**
     * 创建 create 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    public ChatSession create(
            String id,
            String title,
            boolean useKnowledgeBase,
            SearchMode mode,
            int topK,
            long now
    ) {
        ChatSession session = new ChatSession(
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
                title == null || title.isBlank() ? "新对话" : title.trim(),
                null,
                0,
                useKnowledgeBase,
                mode == null ? SearchMode.HYBRID : mode,
                normalizeTopK(topK),
                false,
                now,
                now
        );
        if (chatSessionMapper.insertSession(session) > 0) {
            return session;
        }
        return findById(session.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Chat session id already exists but is not active: " + session.id()
                ));
    }

    /**
     * 确保 ensure Session 所需前置条件存在。
     * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
     */
    public ChatSession ensureSession(
            String conversationId,
            String fallbackTitle,
            boolean useKnowledgeBase,
            SearchMode mode,
            int topK,
            long now
    ) {
        if (conversationId != null && !conversationId.isBlank()) {
            Optional<ChatSession> existing = findById(conversationId);
            if (existing.isPresent()) {
                /**
                 * 更新 update Options 对应的数据。
                 * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
                 */
                updateOptions(conversationId, null, useKnowledgeBase, mode, topK, now);
                return findById(conversationId).orElse(existing.get());
            }
            /*
             * SSE meta 会把 conversationId 提前返回给前端。首次发送时必须用同一个
             * id 创建 SQLite 会话，否则前端持有的会话 id 与落库消息会分叉。
             */
            ChatSession session = create(conversationId, fallbackTitle, useKnowledgeBase, mode, topK, now);
            /**
             * 更新 update Options 对应的数据。
             * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
             */
            updateOptions(conversationId, null, useKnowledgeBase, mode, topK, now);
            return findById(conversationId).orElse(session);
        }
        return create(fallbackTitle, useKnowledgeBase, mode, topK, now);
    }

    /**
     * 更新 update Options 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    public void updateOptions(
            String id,
            String title,
            boolean useKnowledgeBase,
            SearchMode mode,
            int topK,
            long updatedAt
    ) {
        chatSessionMapper.updateOptions(
                id,
                title == null || title.isBlank() ? null : title.trim(),
                useKnowledgeBase,
                (mode == null ? SearchMode.HYBRID : mode).name(),
                normalizeTopK(topK),
                updatedAt
        );
    }

    /**
     * 更新 update Summary 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    public void updateSummary(String id, String summary, int coveredSequence, long updatedAt) {
        chatSessionMapper.updateSummary(id, summary, coveredSequence, updatedAt);
    }

    /**
     * 删除 delete Session 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    public boolean deleteSession(String id) {
        /*
         * 删除会话是用户可见的销毁操作，不能只隐藏 chat_sessions。
         * 先删除会话确认 id 有效，再显式清消息，避免依赖 SQLite 外键开关导致历史消息残留。
         */
        if (chatSessionMapper.deleteSession(id) == 0) {
            return false;
        }
        chatSessionMapper.deleteMessages(id);
        return true;
    }

    /**
     * 清理 clear Messages 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    public void clearMessages(String conversationId, long updatedAt) {
        chatSessionMapper.deleteMessages(conversationId);
        chatSessionMapper.resetSessionMessages(conversationId, updatedAt);
    }

    /**
     * 读取 find Messages 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public List<ChatMessage> findMessages(String conversationId) {
        return chatSessionMapper.findMessages(conversationId);
    }

    /**
     * 执行 聊天会话 中的 count Messages 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public int countMessages(String conversationId) {
        return chatSessionMapper.countMessages(conversationId);
    }

    /**
     * 读取 find Messages After 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public List<ChatMessage> findMessagesAfter(String conversationId, int sequence) {
        return chatSessionMapper.findMessagesAfter(conversationId, sequence);
    }

    /**
     * 追加 append Message 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    public ChatMessage appendMessage(
            String conversationId,
            ChatMessageRole role,
            String content,
            ChatMessageStatus status,
            String requestId,
            AgentType agentType,
            SearchMode retrievalMode,
            String sourcesJson,
            int tokenEstimate,
            long createdAt
    ) {
        int nextSequence = chatSessionMapper.nextMessageSequence(conversationId);
        ChatMessage message = new ChatMessage(
                UUID.randomUUID().toString(),
                conversationId,
                nextSequence,
                role,
                content,
                status,
                requestId,
                agentType,
                retrievalMode,
                sourcesJson,
                tokenEstimate,
                createdAt
        );
        chatSessionMapper.insertMessage(message);
        chatSessionMapper.touchSession(conversationId, createdAt);
        return message;
    }

    /**
     * 执行 聊天会话 中的 to Summary 响应 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ChatSessionResponse toSummaryResponse(ChatSessionSummaryRow row) {
        ChatSession session = new ChatSession(
                row.id(),
                row.title(),
                row.summary(),
                row.summaryMessageSequence(),
                row.useKnowledgeBase(),
                row.retrievalMode(),
                row.topK(),
                row.deleted(),
                row.createdAt(),
                row.updatedAt()
        );
        return ChatSessionResponse.summary(session, row.messageCount());
    }

    /**
     * 规范化 Top K 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static int normalizeTopK(int topK) {
        return Math.clamp(topK, 1, 50);
    }
}
