package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeHealthStatus;

/**
 * 全库健康概览中的目录级摘要。
 */
public record KnowledgeFolderHealthSummaryResponse(
        String id,
        String displayName,
        String folderPath,
        boolean enabled,
        KnowledgeHealthStatus status,
        int documentCount,
        int parsedCount,
        int failedCount,
        int unindexedCount,
        int missingLocalFileCount,
        int staleLocalFileCount,
        int chunkCount,
        Long lastIngestedAt,
        Long lastIndexedAt,
        KnowledgeFolderRunResponse lastRun
) {
}
