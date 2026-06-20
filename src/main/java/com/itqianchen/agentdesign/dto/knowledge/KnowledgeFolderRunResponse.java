package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRun;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunOperation;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunStatus;

/**
 * 知识库维护运行记录响应。
 */
public record KnowledgeFolderRunResponse(
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
        long startedAt,
        long completedAt,
        long durationMs,
        String errorMessage
) {
    /**
     * 从领域运行记录构造接口响应。
     *
     * @param run 运行记录
     * @return 响应对象
     */
    public static KnowledgeFolderRunResponse from(KnowledgeFolderRun run) {
        return new KnowledgeFolderRunResponse(
                run.id(),
                run.scopeType(),
                run.scopeId(),
                run.operation(),
                run.status(),
                run.scannedCount(),
                run.parsedCount(),
                run.skippedCount(),
                run.failedCount(),
                run.indexedDocumentCount(),
                run.indexedChunkCount(),
                run.failedDocumentCount(),
                run.startedAt(),
                run.completedAt(),
                run.durationMs(),
                run.errorMessage()
        );
    }
}
