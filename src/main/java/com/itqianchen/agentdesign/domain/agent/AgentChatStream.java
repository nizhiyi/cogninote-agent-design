package com.itqianchen.agentdesign.domain.agent;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.ChatContextUsageResponse;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import java.util.List;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;

/**
 * 智能体 Chat Stream 是 聊天会话 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record AgentChatStream(
        String requestId,
        String conversationId,
        SearchMode retrievalMode,
        List<RagSourceResponse> sources,
        ChatContextUsageResponse contextUsage,
        Supplier<ChatContextUsageResponse> contextUsageSupplier,
        Flux<String> answer,
        Runnable onCancel
) {
    /**
     * 执行 聊天会话 中的 current Context Usage 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ChatContextUsageResponse currentContextUsage() {
        return contextUsageSupplier == null ? contextUsage : contextUsageSupplier.get();
    }
}
