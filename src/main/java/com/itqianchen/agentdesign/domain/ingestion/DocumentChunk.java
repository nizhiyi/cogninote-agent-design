package com.itqianchen.agentdesign.domain.ingestion;

/**
 * Document Chunk 是 文档管理 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record DocumentChunk(
        int chunkIndex,
        String content,
        Integer pageNumber,
        String heading,
        int tokenCount
) {
}


