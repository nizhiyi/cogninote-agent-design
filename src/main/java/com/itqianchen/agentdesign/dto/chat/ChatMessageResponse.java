package com.itqianchen.agentdesign.dto.chat;

import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import java.util.List;

/**
 * Chat Message 响应 定义返回给前端的 聊天会话 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record ChatMessageResponse(
        String id,
        ChatMessageRole role,
        String content,
        String status,
        String requestId,
        SearchMode retrievalMode,
        List<RagSourceResponse> sources,
        long createdAt
) {

    /**
     * 将领域对象转换为 ChatMessageResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
    public static ChatMessageResponse from(ChatMessage message, List<RagSourceResponse> sources) {
        return new ChatMessageResponse(
                message.id(),
                message.role(),
                message.content(),
                message.status().name().toLowerCase(),
                message.requestId(),
                message.retrievalMode(),
                sources,
                message.createdAt()
        );
    }
}
