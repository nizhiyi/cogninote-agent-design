package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.dto.document.IngestFailureResponse;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import java.util.List;

/**
 * Knowledge Folder Rebuild 响应 定义返回给前端的 知识库 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record KnowledgeFolderRebuildResponse(
        int scannedCount,
        int parsedCount,
        int skippedCount,
        int failedCount,
        List<IngestFailureResponse> failures,
        long indexedDocumentCount,
        long indexedChunkCount,
        long failedDocumentCount,
        long durationMs
) {
    /**
     * 将领域对象转换为 KnowledgeFolderRebuildResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
    public static KnowledgeFolderRebuildResponse from(
            IngestDocumentsResponse ingestResponse,
            RebuildIndexResponse rebuildResponse
    ) {
        return new KnowledgeFolderRebuildResponse(
                ingestResponse.scannedCount(),
                ingestResponse.parsedCount(),
                ingestResponse.skippedCount(),
                ingestResponse.failedCount(),
                ingestResponse.failures(),
                rebuildResponse.indexedDocumentCount(),
                rebuildResponse.indexedChunkCount(),
                rebuildResponse.failedDocumentCount(),
                rebuildResponse.durationMs()
        );
    }
}
