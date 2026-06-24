package com.itqianchen.agentdesign.domain.dto.graph;


import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphScopeType;
/**
 * 已生成图谱摘要。
 *
 * <p>用于图谱页首屏清单；完整 MINDMAP/GRAPH payload 仍由 view 接口按需读取。</p>
 *
 * @param scopeType 图谱范围类型，取值与后端 KnowledgeGraphScopeType 保持一致
 * @param scopeId 范围 ID；全库范围为 null，目录和文档范围保留原始业务 ID
 * @param scopeName 用户可读标题，目录或文档被删除时由服务层提供兜底名称
 * @param scopeSubtitle 用户可读路径；业务对象缺失时保留原始 scopeId 方便定位旧缓存
 * @param nodeCount 当前范围已落库的节点数量
 * @param edgeCount 当前范围已落库的关系数量
 * @param mindmapReady 是否已有思维导图视图
 * @param graphReady 是否已有关系图视图
 * @param generatedAt 同一 scope 下最新视图的更新时间
 * @param latestRun 当前范围最近一次生成任务；没有运行记录时为 null
 */
public record KnowledgeGraphSummaryResponse(
        String scopeType,
        String scopeId,
        String scopeName,
        String scopeSubtitle,
        int nodeCount,
        int edgeCount,
        boolean mindmapReady,
        boolean graphReady,
        Long generatedAt,
        KnowledgeGraphRunResponse latestRun
) {
}
