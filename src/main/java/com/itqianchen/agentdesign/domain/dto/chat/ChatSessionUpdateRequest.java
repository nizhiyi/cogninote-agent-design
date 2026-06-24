package com.itqianchen.agentdesign.domain.dto.chat;

import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import jakarta.validation.constraints.Size;

/**
 * Chat Session Update 请求 定义 聊天会话 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record ChatSessionUpdateRequest(
        @Size(max = 120) String title,
        Boolean useKnowledgeBase,
        SearchMode mode,
        Integer topK
) {
}
