package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import java.util.List;

/**
 * Knowledge Context 是 智能体编排 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record KnowledgeContext(
        SearchMode retrievalMode,
        List<RagSourceResponse> sources
) {
}
