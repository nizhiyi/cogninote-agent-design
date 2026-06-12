package com.itqianchen.agentdesign.service.chat;

import java.util.List;

/**
 * 某次模型调用可使用的历史上下文快照。
 *
 * <p>summary 是较早消息的压缩内容，recentMessages 是预算内保留的原文；lastIncludedSequence
 * 用于摘要写回时确定已经覆盖到哪条消息。</p>
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
