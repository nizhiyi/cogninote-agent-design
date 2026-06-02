package com.itqianchen.agentdesign.document;

public record KnowledgeChunk(
        String id,
        String documentId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading,
        int tokenCount,
        long createdAt
) {
}
