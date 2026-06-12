package com.itqianchen.agentdesign.domain.chat;

import com.itqianchen.agentdesign.domain.search.SearchMode;

/**
 * 聊天会话的持久化设置和摘要进度。
 *
 * <p>summaryMessageSequence 标记摘要已经覆盖到哪条消息，记忆注入时会从该序号之后读取原文，
 * 避免摘要内容和 recent messages 重复进入模型上下文。</p>
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
