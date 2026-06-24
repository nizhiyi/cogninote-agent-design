package com.itqianchen.agentdesign.domain.dto.knowledge;

import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeHealthStatus;
import java.util.List;

/**
 * 全库健康诊断响应。
 */
public record KnowledgeHealthResponse(
        KnowledgeHealthStatus status,
        KnowledgeHealthSummaryResponse summary,
        List<KnowledgeHealthIssueResponse> issues,
        List<KnowledgeFolderHealthSummaryResponse> folders,
        List<KnowledgeFolderRunResponse> currentRuns,
        List<KnowledgeFolderRunResponse> queuedRuns,
        KnowledgeFolderRunResponse latestRun
) {
}
