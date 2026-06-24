package com.itqianchen.agentdesign.domain.vo.search;

/**
 * 准备写入搜索索引的文档片段。
 *
 * <p>字段必须和 SQLite chunk 快照保持一致，Lucene 重建失败时会重新从该结构恢复索引内容。</p>
 */
public record IndexedChunk(
        String id,
        String documentId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading
) {
}


