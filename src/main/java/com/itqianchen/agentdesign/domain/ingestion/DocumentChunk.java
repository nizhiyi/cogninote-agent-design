package com.itqianchen.agentdesign.domain.ingestion;

/**
 * 解析流程切分出的内存态片段。
 *
 * <p>该类型还没有数据库 id，后续持久化会补齐 documentId 和 contentHash；这里保留页码与标题，
 * 让不同解析器的输出在切片后仍能回溯原文位置。</p>
 */
public record DocumentChunk(
        int chunkIndex,
        String content,
        Integer pageNumber,
        String heading,
        int tokenCount
) {
}


