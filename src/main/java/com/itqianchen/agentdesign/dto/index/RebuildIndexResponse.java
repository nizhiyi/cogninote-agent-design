package com.itqianchen.agentdesign.dto.index;

public record RebuildIndexResponse(
        long indexedDocumentCount,
        long indexedChunkCount,
        long failedDocumentCount,
        long durationMs
) {
}


