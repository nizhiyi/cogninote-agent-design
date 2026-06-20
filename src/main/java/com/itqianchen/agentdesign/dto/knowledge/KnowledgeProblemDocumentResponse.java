package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;

/**
 * 健康抽屉中展示的问题文档。
 */
public record KnowledgeProblemDocumentResponse(
        String documentId,
        String sourcePath,
        String fileName,
        String message,
        long updatedAt
) {
    /**
     * 从文档记录构造问题文档响应。
     *
     * @param document 文档记录
     * @param message 问题说明
     * @return 问题文档响应
     */
    public static KnowledgeProblemDocumentResponse from(KnowledgeDocument document, String message) {
        return new KnowledgeProblemDocumentResponse(
                document.id(),
                document.sourcePath(),
                document.fileName(),
                message,
                document.updatedAt()
        );
    }
}
