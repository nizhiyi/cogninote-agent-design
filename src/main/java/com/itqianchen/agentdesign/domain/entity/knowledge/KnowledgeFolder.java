package com.itqianchen.agentdesign.domain.entity.knowledge;

/**
 * 用户导入的本地知识库目录。
 *
 * <p>folderPath 是规范化后的绝对路径，recursive 和 enabled 会影响后续扫描与检索可见性；
 * 停用目录只应移除索引命中，不应删除 SQLite 中的文档解析结果。</p>
 */
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
