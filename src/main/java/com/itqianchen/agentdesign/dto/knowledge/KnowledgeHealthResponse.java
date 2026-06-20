package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeHealthStatus;
import java.util.List;

/**
 * 全库健康诊断响应。
 */
public record KnowledgeHealthResponse(
        KnowledgeHealthStatus status,
        KnowledgeHealthSummaryResponse summary,
        List<KnowledgeHealthIssueResponse> issues,
        List<KnowledgeFolderHealthSummaryResponse> folders
) {
}
