package com.itqianchen.agentdesign.domain.dto.knowledge;


import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeHealthIssueCode;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunScopeType;
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
        List<String> examples,
        List<KnowledgeHealthIssueExampleResponse> exampleDetails
) {
    public KnowledgeHealthIssueResponse {
        examples = examples == null ? List.of() : List.copyOf(examples);
        exampleDetails = exampleDetails == null ? List.of() : List.copyOf(exampleDetails);
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
        this(code, severity, message, action, scopeType, scopeId, count, List.of(), List.of());
    }

    public KnowledgeHealthIssueResponse(
            KnowledgeHealthIssueCode code,
            String severity,
            String message,
            String action,
            KnowledgeFolderRunScopeType scopeType,
            String scopeId,
            int count,
            List<String> examples
    ) {
        this(code, severity, message, action, scopeType, scopeId, count, examples, List.of());
    }
}
