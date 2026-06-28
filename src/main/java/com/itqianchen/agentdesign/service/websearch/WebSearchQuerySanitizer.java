package com.itqianchen.agentdesign.service.websearch;

/**
 * 联网搜索 query 归一化工具。
 *
 * <p>搜索词来自模型工具调用，必须在进入外部 API 前做长度和空白控制，避免异常长输入造成费用和日志风险。</p>
 */
final class WebSearchQuerySanitizer {

    private static final int MAX_QUERY_CHARS = 300;

    private WebSearchQuerySanitizer() {
    }

    static String normalize(String query) {
        if (query == null) {
            return "";
        }
        String normalized = query.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_QUERY_CHARS
                ? normalized
                : normalized.substring(0, MAX_QUERY_CHARS);
    }
}
