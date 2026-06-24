package com.itqianchen.agentdesign.domain.vo.knowledge;


import com.itqianchen.agentdesign.domain.entity.knowledge.KnowledgeFolder;
/**
 * 知识库目录列表页使用的聚合统计。
 *
 * <p>计数来自 SQLite 当前状态，用于提示解析、索引和失败情况；它不是 Lucene 的实时命中统计。</p>
 */
public record KnowledgeFolderSummary(
        KnowledgeFolder folder,
        int documentCount,
        int parsedCount,
        int failedCount,
        int chunkCount,
        int unindexedCount
) {
}
