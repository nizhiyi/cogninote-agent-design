package com.itqianchen.agentdesign.domain.document;

public record KnowledgeDocument(
        String id,
        String sourcePath,
        String fileName,
        FileType fileType,
        long fileSize,
        long lastModified,
        String contentHash,
        DocumentStatus status,
        Long indexedAt,
        long createdAt,
        long updatedAt,
        int chunkCount
) {
}


