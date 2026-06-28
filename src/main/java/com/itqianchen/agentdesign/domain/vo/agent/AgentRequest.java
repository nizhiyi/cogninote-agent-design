package com.itqianchen.agentdesign.domain.vo.agent;

import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import com.itqianchen.agentdesign.domain.dto.chat.ChatReferenceRequest;
import com.itqianchen.agentdesign.domain.dto.chat.ChatStreamRequest;
import java.util.List;

/**
 * 智能体 请求 定义 智能体编排 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record AgentRequest(
        String requestId,
        String question,
        Integer topK,
        SearchMode mode,
        String conversationId,
        boolean useKnowledgeBase,
        boolean useWebSearch,
        List<ChatReferenceRequest> references
) {

    /**
     * 兼容旧测试和内部调用，未显式传引用时按空引用处理。
     */
    public AgentRequest(
            String requestId,
            String question,
            Integer topK,
            SearchMode mode,
            String conversationId,
            boolean useKnowledgeBase
    ) {
        this(requestId, question, topK, mode, conversationId, useKnowledgeBase, false, List.of());
    }

    /**
     * 兼容旧测试和内部调用，未显式传引用时按空引用处理。
     */
    public AgentRequest(
            String requestId,
            String question,
            Integer topK,
            SearchMode mode,
            String conversationId,
            boolean useKnowledgeBase,
            List<ChatReferenceRequest> references
    ) {
        this(requestId, question, topK, mode, conversationId, useKnowledgeBase, false, references);
    }

    /**
     * 兼容旧测试和内部调用，未显式传引用时按空引用处理。
     */
    public AgentRequest(
            String requestId,
            String question,
            Integer topK,
            SearchMode mode,
            String conversationId,
            boolean useKnowledgeBase,
            boolean useWebSearch
    ) {
        this(requestId, question, topK, mode, conversationId, useKnowledgeBase, useWebSearch, List.of());
    }

    /**
     * 从 SSE 请求构造 agent 入参。
     *
     * <p>question 在进入 agent 前统一 trim；useKnowledgeBase 缺省为 true 以兼容旧前端请求。</p>
     */
    public static AgentRequest from(ChatStreamRequest request) {
        return new AgentRequest(
                request.requestId(),
                request.question().trim(),
                request.topK(),
                request.mode(),
                request.conversationId(),
                request.useKnowledgeBase() == null || request.useKnowledgeBase(),
                Boolean.TRUE.equals(request.useWebSearch()),
                request.references() == null ? List.of() : request.references()
        );
    }
}
