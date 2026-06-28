package com.itqianchen.agentdesign.domain.dto.websearch;

/**
 * 联网搜索连通性测试响应。
 *
 * <p>测试只验证 provider 调用链，不写入聊天记录，也不返回 API Key 或完整调试信息。</p>
 */
public record WebSearchTestResponse(
        boolean success,
        String message,
        int resultCount
) {
}
