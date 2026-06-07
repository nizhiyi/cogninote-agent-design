package com.itqianchen.agentdesign.service.agent;

public record QueryContextualization(
        String originalQuestion,
        String retrievalQuery,
        boolean rewritten,
        String reason,
        double confidence
) {

    public static QueryContextualization original(String question, String reason) {
        return new QueryContextualization(question, question, false, reason, 0.0);
    }
}
