package com.itqianchen.agentdesign.domain.graph;

/**
 * 知识图谱 scope 的规范化表示。
 * <p>数据库查询统一使用这里清洗后的 scopeType/scopeId，避免 NULL 和空字符串混用。</p>
 */
public record KnowledgeGraphScope(
        KnowledgeGraphScopeType scopeType,
        String scopeId,
        String displayName
) {
    /**
     * 返回数据库查询使用的 scopeId。
     *
     * <p>ALL 范围统一使用 null 表示全局，避免查询层同时处理 null 和空字符串两种全局语义。</p>
     *
     * @return 归一化后的 scopeId
     */
    public String normalizedScopeId() {
        return scopeType == KnowledgeGraphScopeType.ALL ? null : scopeId;
    }
}
