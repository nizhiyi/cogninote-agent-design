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
        int newLocalFileCount,
        int chunkCount,
        Long lastIngestedAt,
        Long lastIndexedAt,
        long luceneDocumentCount,
        long luceneChunkCount,
        boolean embeddingConfigured,
        boolean indexConsistent,
        int runningRunCount,
        int queuedRunCount,
        boolean answerReady,
        int searchableDocumentCount,
        int syncIssueCount,
        int retrievalIssueCount,
        int conflictIssueCount,
        int graphStaleCount
) {
}
