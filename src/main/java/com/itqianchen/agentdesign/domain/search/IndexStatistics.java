package com.itqianchen.agentdesign.domain.search;

/**
 * 搜索索引页展示的 SQLite 侧索引进度。
 *
 * <p>parsedDocumentCount 与 unindexedDocumentCount 来自文档表，表示哪些文档理论上应该进入索引；
 * lastIndexedAt 是应用记录的最后索引时间，不代表 Lucene 目录文件的修改时间。</p>
 */
public record IndexStatistics(
        long parsedDocumentCount,
        long unindexedDocumentCount,
        Long lastIndexedAt
) {
}


