package com.itqianchen.agentdesign.dto.document;

import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;

/**
 * Document Summary 响应 定义返回给前端的 文档管理 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record DocumentSummaryResponse(
        String id,
        String knowledgeFolderId,
        String sourcePath,
        String fileName,
        String fileType,
        long fileSize,
        long lastModified,
        String contentHash,
        String status,
        Long indexedAt,
        long createdAt,
        long updatedAt,
        int chunkCount
) {
    /**
     * 将领域对象转换为 DocumentSummaryResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
    public static DocumentSummaryResponse from(KnowledgeDocument document) {
        return new DocumentSummaryResponse(
                document.id(),
                document.knowledgeFolderId(),
                document.sourcePath(),
                document.fileName(),
                document.fileType().name(),
                document.fileSize(),
                document.lastModified(),
                document.contentHash(),
                document.status().name(),
                document.indexedAt(),
                document.createdAt(),
                document.updatedAt(),
                document.chunkCount()
        );
    }
}


