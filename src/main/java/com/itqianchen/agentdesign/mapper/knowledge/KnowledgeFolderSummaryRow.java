package com.itqianchen.agentdesign.mapper.knowledge;

/**
 * Knowledge Folder Summary Row 表示 知识库 查询返回的数据库行投影。
 * <p>字段需要和 Mapper SQL 别名保持一致。</p>
 */
public record KnowledgeFolderSummaryRow(
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
        int unindexedCount
) {
}
