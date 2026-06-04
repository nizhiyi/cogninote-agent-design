package com.itqianchen.agentdesign.domain.knowledge;

public record KnowledgeFolder(
        String id,
        String folderPath,
        String displayName,
        boolean recursive,
        boolean enabled,
        Long lastIngestedAt,
        Long lastIndexedAt,
        long createdAt,
        long updatedAt
) {
}
