package com.itqianchen.agentdesign.domain.search;

public record StoredChunk(
        String chunkId,
        String documentId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading,
        String fileName,
        String sourcePath
) {
}


