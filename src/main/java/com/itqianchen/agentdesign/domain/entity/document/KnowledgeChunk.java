package com.itqianchen.agentdesign.domain.entity.document;

/**
 * 文档解析后落库的最小检索片段。
 *
 * <p>chunkIndex 在单文档内保持稳定顺序，pageNumber 和 heading 保留来源定位信息；
 * contentHash 用于索引重建时识别片段内容版本。</p>
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


