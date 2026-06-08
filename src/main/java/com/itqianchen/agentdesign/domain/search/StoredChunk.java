package com.itqianchen.agentdesign.domain.search;

/**
 * Stored Chunk 是 检索索引 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record StoredChunk(
        String chunkId,
        String documentId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading,
        String fileName,
        String sourcePath
) {
}


