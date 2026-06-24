package com.itqianchen.agentdesign.domain.entity.graph;


import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphScopeType;
/**
 * scope 内合并后的图谱关系。
 *
 * <p>{@code relationType} 只保存内部粗分类，用于筛选、统计和配色；用户可见的短谓词必须读取
 * {@code displayLabel}，完整关系语义读取 {@code description}。</p>
 */
public record KnowledgeGraphEdge(
        String id,
        KnowledgeGraphScopeType scopeType,
        String scopeId,
        String sourceNodeId,
        String targetNodeId,
        String relationType,
        String displayLabel,
        String description,
        double confidence,
        int mentionCount,
        long createdAt,
        long updatedAt
) {
}
