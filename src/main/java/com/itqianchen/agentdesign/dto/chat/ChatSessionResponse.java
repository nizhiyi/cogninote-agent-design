package com.itqianchen.agentdesign.dto.chat;

import com.itqianchen.agentdesign.domain.chat.ChatSession;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import java.util.List;

/**
 * Chat Session 响应 定义返回给前端的 聊天会话 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record ChatSessionResponse(
        String id,
        String title,
        String summary,
        boolean useKnowledgeBase,
        SearchMode mode,
        int topK,
        long createdAt,
        long updatedAt,
        int messageCount,
        ChatContextUsageResponse contextUsage,
        List<ChatMessageResponse> messages
) {

    /**
     * 构造不携带消息体的会话摘要。
     *
     * @param session 会话领域对象
     * @return 适合侧栏列表展示的响应
     */
    public static ChatSessionResponse summary(ChatSession session) {
        return summary(session, 0);
    }

    /**
     * 构造带消息数量的会话摘要。
     *
     * <p>摘要响应固定返回空 messages，避免列表接口传输完整历史消息。</p>
     *
     * @param session 会话领域对象
     * @param messageCount 会话消息数量
     * @return 适合侧栏列表展示的响应
     */
    public static ChatSessionResponse summary(ChatSession session, int messageCount) {
        return new ChatSessionResponse(
                session.id(),
                session.title(),
                session.summary(),
                session.useKnowledgeBase(),
                session.retrievalMode(),
                session.topK(),
                session.createdAt(),
                session.updatedAt(),
                messageCount,
                null,
                List.of()
        );
    }

    /**
     * 构造会话详情响应。
     *
     * <p>摘要列表接口使用 summary() 返回空 messages，详情接口才携带完整消息，避免侧栏传输大文本。</p>
     */
    public static ChatSessionResponse from(ChatSession session, List<ChatMessageResponse> messages) {
        return new ChatSessionResponse(
                session.id(),
                session.title(),
                session.summary(),
                session.useKnowledgeBase(),
                session.retrievalMode(),
                session.topK(),
                session.createdAt(),
                session.updatedAt(),
                messages.size(),
                null,
                messages
        );
    }

    /**
     * 返回附带上下文预算的会话响应副本。
     *
     * <p>record 保持不可变，追加流式上下文统计时通过副本传递，避免修改既有响应对象。</p>
     *
     * @param contextUsage 上下文预算统计
     * @return 包含上下文预算的响应副本
     */
    public ChatSessionResponse withContextUsage(ChatContextUsageResponse contextUsage) {
        return new ChatSessionResponse(
                id,
                title,
                summary,
                useKnowledgeBase,
                mode,
                topK,
                createdAt,
                updatedAt,
                messageCount,
                contextUsage,
                messages
        );
    }
}
