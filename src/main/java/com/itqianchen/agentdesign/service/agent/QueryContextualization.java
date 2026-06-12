package com.itqianchen.agentdesign.service.agent;

/**
 * 多轮问题改写结果。
 *
 * <p>originalQuestion 始终用于最终回答，retrievalQuery 只用于召回知识库；confidence 和 reason
 * 便于后续观察何时应该回退到原问题。</p>
 */
public record QueryContextualization(
        String originalQuestion,
        String retrievalQuery,
        boolean rewritten,
        String reason,
        double confidence
) {

    /**
     * 构造未改写结果。
     *
     * <p>检索 query 与原问题保持一致，避免低置信度改写污染知识库召回。</p>
     */
    public static QueryContextualization original(String question, String reason) {
        return new QueryContextualization(question, question, false, reason, 0.0);
    }
}
