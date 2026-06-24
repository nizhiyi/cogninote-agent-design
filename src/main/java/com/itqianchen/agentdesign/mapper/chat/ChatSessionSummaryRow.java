package com.itqianchen.agentdesign.mapper.chat;

import com.itqianchen.agentdesign.domain.enums.search.SearchMode;

/**
 * Chat Session Summary Row 表示 聊天会话 查询返回的数据库行投影。
 * <p>字段需要和 Mapper SQL 别名保持一致。</p>
 */
public record ChatSessionSummaryRow(
        String id,
        String title,
        String summary,
        int summaryMessageSequence,
        boolean useKnowledgeBase,
        SearchMode retrievalMode,
        int topK,
        boolean deleted,
        long createdAt,
        long updatedAt,
        int messageCount
) {
}
