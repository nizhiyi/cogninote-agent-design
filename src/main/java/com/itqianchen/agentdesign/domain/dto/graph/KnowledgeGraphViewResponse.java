package com.itqianchen.agentdesign.domain.dto.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphView;

/**
 * 图谱派生视图响应。
 */
public record KnowledgeGraphViewResponse(
        String viewType,
        JsonNode payload,
        String generatedFromRunId,
        long createdAt,
        long updatedAt
) {
    /**
     * 将持久化视图和已解析 payload 组装为接口响应。
     *
     * <p>payload 由服务层解析成 JsonNode，避免 DTO 层重新处理 JSON 字符串并吞掉解析异常。</p>
     *
     * @param view 图谱派生视图元数据
     * @param payload 已解析的视图内容
     * @return 图谱视图响应
     */
    public static KnowledgeGraphViewResponse from(KnowledgeGraphView view, JsonNode payload) {
        return new KnowledgeGraphViewResponse(
                view.viewType().name(),
                payload,
                view.generatedFromRunId(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
