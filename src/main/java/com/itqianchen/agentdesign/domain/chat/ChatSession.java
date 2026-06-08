package com.itqianchen.agentdesign.domain.chat;

import com.itqianchen.agentdesign.domain.search.SearchMode;

/**
 * Chat Session 是 聊天会话 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record ChatSession(
        String id,
        String title,
        String summary,
        int summaryMessageSequence,
        boolean useKnowledgeBase,
        SearchMode retrievalMode,
        int topK,
        boolean deleted,
        long createdAt,
        long updatedAt
) {
}
