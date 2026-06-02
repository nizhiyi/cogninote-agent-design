package com.itqianchen.agentdesign.ingestion;

public record DocumentChunk(
        int chunkIndex,
        String content,
        Integer pageNumber,
        String heading,
        int tokenCount
) {
}
