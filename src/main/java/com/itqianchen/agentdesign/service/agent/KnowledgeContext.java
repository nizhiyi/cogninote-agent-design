package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import com.itqianchen.agentdesign.domain.dto.chat.RagSourceResponse;
import java.util.List;

/**
 * RAG 检索后交给 Agent 的上下文快照。
 *
 * <p>retrievalMode 是实际执行的模式，可能因为 Embedding 不可用从 HYBRID/VECTOR 降级为 KEYWORD；
 * sources 已按回答引用编号排序。</p>
 */
public record KnowledgeContext(
        SearchMode retrievalMode,
        List<RagSourceResponse> sources
) {
}
