package com.itqianchen.agentdesign.domain.document;

/**
 * Knowledge Document 是 知识库 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
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
    /**
     * 注入 KnowledgeDocument 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
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


