package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库维护任务记录。
 *
 * <p>该记录既是本地 FIFO 队列的恢复事实源，也是完成后的维护历史。未结束任务的
 * startedAt、completedAt 和 durationMs 可以为空；调用方必须以 status 判断任务生命周期。</p>
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
        String phase,
        long progressCurrent,
        long progressTotal,
        String currentItem,
        Long queuedAt,
        Long startedAt,
        Long completedAt,
        Long durationMs,
        String errorMessage,
        long createdAt,
        long updatedAt
) {
}
