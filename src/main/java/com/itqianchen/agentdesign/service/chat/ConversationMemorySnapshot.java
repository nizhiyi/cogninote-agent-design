package com.itqianchen.agentdesign.service.chat;

import java.util.List;

/**
 * Conversation Memory Snapshot 是 聊天会话 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record ConversationMemorySnapshot(
        String summary,
        List<ConversationMemoryEntry> recentMessages,
        int lastIncludedSequence,
        int summaryTokens,
        int recentMessageTokens,
        int totalMessageCount,
        int contextWindowTokens,
        int historyBudgetTokens,
        String estimationMethod
) {
}
