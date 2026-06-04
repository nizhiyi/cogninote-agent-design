package com.itqianchen.agentdesign.domain.knowledge;

public record KnowledgeFolderSummary(
        KnowledgeFolder folder,
        int documentCount,
        int parsedCount,
        int failedCount,
        int chunkCount,
        int unindexedCount
) {
}
