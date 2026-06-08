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
     * 执行 聊天会话 中的 summary 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public static ChatSessionResponse summary(ChatSession session) {
        return summary(session, 0);
    }

    /**
     * 执行 聊天会话 中的 summary 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 将领域对象转换为 ChatSessionResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
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
     * 返回应用 with Context Usage 后的新对象。
     * <p>不可变数据通过复制表达变更，避免调用方误改原对象。</p>
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
