package com.itqianchen.agentdesign.service.agent;

/**
 * Query Contextualization 是 智能体编排 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record QueryContextualization(
        String originalQuestion,
        String retrievalQuery,
        boolean rewritten,
        String reason,
        double confidence
) {

    /**
     * 执行 智能体编排 中的 original 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public static QueryContextualization original(String question, String reason) {
        return new QueryContextualization(question, question, false, reason, 0.0);
    }
}
