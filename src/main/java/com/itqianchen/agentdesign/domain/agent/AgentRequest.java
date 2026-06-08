package com.itqianchen.agentdesign.domain.agent;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;

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
        boolean useKnowledgeBase
) {

    /**
     * 将领域对象转换为 AgentRequest。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
    public static AgentRequest from(ChatStreamRequest request) {
        return new AgentRequest(
                request.requestId(),
                request.question().trim(),
                request.topK(),
                request.mode(),
                request.conversationId(),
                request.useKnowledgeBase() == null || request.useKnowledgeBase()
        );
    }
}
