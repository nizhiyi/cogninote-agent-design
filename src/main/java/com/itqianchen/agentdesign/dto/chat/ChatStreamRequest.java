package com.itqianchen.agentdesign.dto.chat;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Chat Stream 请求 定义 聊天会话 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record ChatStreamRequest(
        @NotBlank @Size(max = 4000) String question,
        Integer topK,
        SearchMode mode,
        @Size(max = 80) String requestId,
        @Size(max = 80) String conversationId,
        Boolean useKnowledgeBase
) {
}


