package com.itqianchen.agentdesign.dto.document;

import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;

public record DocumentSummaryResponse(
        String id,
        String sourcePath,
        String fileName,
        String fileType,
        long fileSize,
        long lastModified,
        String contentHash,
        String status,
        Long indexedAt,
        long createdAt,
        long updatedAt,
        int chunkCount
) {
    public static DocumentSummaryResponse from(KnowledgeDocument document) {
        return new DocumentSummaryResponse(
                document.id(),
                document.sourcePath(),
                document.fileName(),
                document.fileType().name(),
                document.fileSize(),
                document.lastModified(),
                document.contentHash(),
                document.status().name(),
                document.indexedAt(),
                document.createdAt(),
                document.updatedAt(),
                document.chunkCount()
        );
    }
}


