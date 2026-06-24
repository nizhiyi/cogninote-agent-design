package com.itqianchen.agentdesign.domain.dto.knowledge;

import java.util.List;

/**
 * 健康诊断问题的结构化样例。
 *
 * <p>examples 字符串字段继续保留给旧前端兼容；新 UI 使用该结构拿到可读标题、辅助说明和可执行
 * scope，避免把数据库 ID 暴露给用户或把文档图谱误当成全库图谱处理。</p>
 */
public record KnowledgeHealthIssueExampleResponse(
        String type,
        String label,
        String description,
        String scopeType,
        String scopeId,
        List<String> items
) {
    public KnowledgeHealthIssueExampleResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
