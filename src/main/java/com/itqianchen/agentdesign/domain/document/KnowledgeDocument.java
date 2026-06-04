package com.itqianchen.agentdesign.domain.document;

public record KnowledgeDocument(
        String id,
        String knowledgeFolderId,
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
    public KnowledgeDocument(
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
        this(
                id,
                null,
                sourcePath,
                fileName,
                fileType,
                fileSize,
                lastModified,
                contentHash,
                status,
                indexedAt,
                createdAt,
                updatedAt,
                chunkCount
        );
    }
}


