package com.itqianchen.agentdesign.mapper.graph;

/**
 * 已生成图谱 scope 的轻量聚合行。
 *
 * <p>该行只来自 knowledge_graph_views 的元数据，不读取 payload_json，避免进入图谱页时拉取完整视图。</p>
 *
 * @param scopeType 图谱范围类型
 * @param scopeId 范围 ID；全库范围为 null
 * @param mindmapReady 同一 scope 是否存在 MINDMAP 视图
 * @param graphReady 同一 scope 是否存在 GRAPH 视图
 * @param generatedAt 同一 scope 下最新视图的更新时间
 */
public record KnowledgeGraphSummaryRow(
        String scopeType,
        String scopeId,
        boolean mindmapReady,
        boolean graphReady,
        Long generatedAt
) {
}
