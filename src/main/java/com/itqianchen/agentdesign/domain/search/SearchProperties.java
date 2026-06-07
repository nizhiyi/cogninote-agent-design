package com.itqianchen.agentdesign.domain.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        int topK,
        double bm25Weight,
        double vectorWeight,
        float bm25K1,
        float bm25B,
        int hybridCandidateMultiplier,
        int hybridMinCandidates,
        int rrfK
) {
    public int normalizedTopK(Integer requestedTopK) {
        int value = requestedTopK == null ? topK : requestedTopK;
        return Math.clamp(value, 1, 50);
    }

    public int hybridCandidateLimit(int topK) {
        return Math.max(topK * Math.max(1, hybridCandidateMultiplier), Math.max(1, hybridMinCandidates));
    }

    public int normalizedRrfK() {
        return Math.max(1, rrfK);
    }
}


