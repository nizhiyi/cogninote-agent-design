package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeHealthIssueCode;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import java.util.List;

/**
 * 知识库健康诊断问题。
 *
 * <p>action 是前端可映射到现有同步、重建、启停和删除入口的建议动作，不要求后端自动执行。</p>
 */
public record KnowledgeHealthIssueResponse(
        KnowledgeHealthIssueCode code,
        String severity,
        String message,
        String action,
        KnowledgeFolderRunScopeType scopeType,
        String scopeId,
        int count,
        List<String> examples
) {
    public KnowledgeHealthIssueResponse {
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    public KnowledgeHealthIssueResponse(
            KnowledgeHealthIssueCode code,
            String severity,
            String message,
            String action,
            KnowledgeFolderRunScopeType scopeType,
            String scopeId,
            int count
    ) {
        this(code, severity, message, action, scopeType, scopeId, count, List.of());
    }
}
