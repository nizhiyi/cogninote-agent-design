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
     * 构造前端文档表格使用的摘要。
     *
     * <p>状态和文件类型暴露为枚举名字符串，和前端筛选项保持同一套取值。</p>
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


