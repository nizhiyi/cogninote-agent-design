package com.itqianchen.agentdesign.domain.knowledge;

/**
 * Knowledge Folder Summary 是 知识库 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record KnowledgeFolderSummary(
        KnowledgeFolder folder,
        int documentCount,
        int parsedCount,
        int failedCount,
        int chunkCount,
        int unindexedCount
) {
}
