package com.itqianchen.agentdesign.dto.chat;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import java.util.List;

/**
 * Chat Meta 事件 描述 聊天会话 的事件载荷。
 * <p>主要用于 SSE 流、内部路由或状态传递场景。</p>
 */
public record ChatMetaEvent(
        String requestId,
        String conversationId,
        SearchMode retrievalMode,
        List<RagSourceResponse> sources,
        ChatContextUsageResponse contextUsage
) {
    /**
     * 构造 SSE meta 事件。
     *
     * <p>meta 只用于初始化前端流状态，来源列表不携带完整 chunk 内容，避免首个事件过大影响流式首包。</p>
     *
     * @param requestId 当前流请求 ID
     * @param conversationId 会话 ID
     * @param retrievalMode 检索模式
     * @param sources RAG 来源列表
     * @param contextUsage 上下文预算快照
     */
    public ChatMetaEvent(
            String requestId,
            String conversationId,
            SearchMode retrievalMode,
            List<RagSourceResponse> sources,
            ChatContextUsageResponse contextUsage
    ) {
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.retrievalMode = retrievalMode;
        this.sources = sources.stream()
                .map(source -> source.withContent(null))
                .toList();
        this.contextUsage = contextUsage;
    }
}


