package com.itqianchen.agentdesign.dto.index;

public record IndexStatusResponse(
        String indexPath,
        long indexedDocumentCount,
        long indexedChunkCount,
        long parsedDocumentCount,
        long unindexedDocumentCount,
        Long lastIndexedAt,
        boolean embeddingConfigured
) {
}


