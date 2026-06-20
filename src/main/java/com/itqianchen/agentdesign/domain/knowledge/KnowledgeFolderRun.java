package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库维护动作的历史记录。
 *
 * <p>run 只记录已经发生的导入、同步、重建、启停和删除结果；它不是后台任务队列。
 * 这些字段会写入本地 SQLite 并暴露给健康面板，新增字段时需要同步 schema、Mapper 和响应 DTO。</p>
 */
public record KnowledgeFolderRun(
        String id,
        KnowledgeFolderRunScopeType scopeType,
        String scopeId,
        KnowledgeFolderRunOperation operation,
        KnowledgeFolderRunStatus status,
        int scannedCount,
        int parsedCount,
        int skippedCount,
        int failedCount,
        long indexedDocumentCount,
        long indexedChunkCount,
        long failedDocumentCount,
        String failuresJson,
        long startedAt,
        long completedAt,
        long durationMs,
        String errorMessage,
        long createdAt
) {
}
