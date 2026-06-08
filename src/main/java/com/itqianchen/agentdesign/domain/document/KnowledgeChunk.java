package com.itqianchen.agentdesign.domain.document;

/**
 * Knowledge Chunk 是 知识库 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record KnowledgeChunk(
        String id,
        String documentId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading,
        int tokenCount,
        long createdAt
) {
}


