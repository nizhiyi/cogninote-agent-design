package com.itqianchen.agentdesign.domain.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Search 配置属性 映射 检索索引 的 YAML 配置。
 * <p>通过类型化配置隔离环境变量、默认值和业务代码。</p>
 */
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
    /**
     * 规范化 normalized Top K 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    public int normalizedTopK(Integer requestedTopK) {
        int value = requestedTopK == null ? topK : requestedTopK;
        return Math.clamp(value, 1, 50);
    }

    /**
     * 执行 检索索引 中的 hybrid Candidate Limit 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public int hybridCandidateLimit(int topK) {
        return Math.max(topK * Math.max(1, hybridCandidateMultiplier), Math.max(1, hybridMinCandidates));
    }

    /**
     * 规范化 normalized Rrf K 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    public int normalizedRrfK() {
        return Math.max(1, rrfK);
    }
}


