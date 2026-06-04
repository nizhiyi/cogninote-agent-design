package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderSummary;
import com.itqianchen.agentdesign.dto.document.DocumentSummaryResponse;
import java.util.List;

public record KnowledgeFolderResponse(
        String id,
        String folderPath,
        String displayName,
        boolean recursive,
        boolean enabled,
        Long lastIngestedAt,
        Long lastIndexedAt,
        long createdAt,
        long updatedAt,
        int documentCount,
        int parsedCount,
        int failedCount,
        int chunkCount,
        int unindexedCount,
        List<DocumentSummaryResponse> documents
) {
    public static KnowledgeFolderResponse from(
            KnowledgeFolderSummary summary,
            List<DocumentSummaryResponse> documents
    ) {
        return new KnowledgeFolderResponse(
                summary.folder().id(),
                summary.folder().folderPath(),
                summary.folder().displayName(),
                summary.folder().recursive(),
                summary.folder().enabled(),
                summary.folder().lastIngestedAt(),
                summary.folder().lastIndexedAt(),
                summary.folder().createdAt(),
                summary.folder().updatedAt(),
                summary.documentCount(),
                summary.parsedCount(),
                summary.failedCount(),
                summary.chunkCount(),
                summary.unindexedCount(),
                documents
        );
    }
}
