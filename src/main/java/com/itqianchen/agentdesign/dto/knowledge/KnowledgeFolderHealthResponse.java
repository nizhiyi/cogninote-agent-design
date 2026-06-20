package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeHealthStatus;
import java.util.List;

/**
 * 单个知识库目录的健康诊断响应。
 */
public record KnowledgeFolderHealthResponse(
        String folderId,
        KnowledgeHealthStatus status,
        List<KnowledgeHealthIssueResponse> issues,
        List<KnowledgeProblemDocumentResponse> failedDocuments,
        List<KnowledgeProblemDocumentResponse> unindexedDocuments,
        List<KnowledgeProblemDocumentResponse> missingLocalFiles,
        List<KnowledgeProblemDocumentResponse> staleLocalFiles,
        List<KnowledgeFolderRunResponse> runs
) {
}
