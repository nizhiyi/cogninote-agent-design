package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderSummary;
import com.itqianchen.agentdesign.dto.document.DocumentSummaryResponse;
import java.util.List;

/**
 * Knowledge Folder 响应 定义返回给前端的 知识库 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
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
    /**
     * 将领域对象转换为 KnowledgeFolderResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
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
