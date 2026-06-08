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
     * 注入 ChatMetaEvent 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
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


