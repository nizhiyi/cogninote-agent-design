package com.itqianchen.agentdesign.domain.agent;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.ChatContextUsageResponse;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import java.util.List;

/**
 * 智能体 事件 描述 智能体编排 的事件载荷。
 * <p>主要用于 SSE 流、内部路由或状态传递场景。</p>
 */
public sealed interface AgentEvent permits AgentEvent.Meta, AgentEvent.Delta, AgentEvent.Done, AgentEvent.Error {

    /**
     * Meta 是 智能体编排 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    record Meta(
            String requestId,
            String conversationId,
            SearchMode retrievalMode,
            List<RagSourceResponse> sources,
            ChatContextUsageResponse contextUsage
    ) implements AgentEvent {
    }

    /**
     * Delta 是 智能体编排 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    record Delta(String text) implements AgentEvent {
    }

    /**
     * Done 是 智能体编排 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    record Done(Object usage, ChatContextUsageResponse contextUsage) implements AgentEvent {
    }

    /**
     * Error 是 智能体编排 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    record Error(String message) implements AgentEvent {
    }
}
