package com.itqianchen.agentdesign.domain.knowledge;

/**
 * Knowledge Folder 是 知识库 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
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
