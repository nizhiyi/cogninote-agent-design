package com.itqianchen.agentdesign.dto.knowledge;

/**
 * 知识库健康概览统计。
 */
public record KnowledgeHealthSummaryResponse(
        int folderCount,
        int enabledFolderCount,
        int documentCount,
        int parsedCount,
        int failedCount,
        int unindexedCount,
        int missingLocalFileCount,
        int staleLocalFileCount,
        int chunkCount,
        Long lastIngestedAt,
        Long lastIndexedAt
) {
}
