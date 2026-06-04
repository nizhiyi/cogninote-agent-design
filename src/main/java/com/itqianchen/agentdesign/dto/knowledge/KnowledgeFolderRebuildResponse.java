package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.dto.document.IngestFailureResponse;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import java.util.List;

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
