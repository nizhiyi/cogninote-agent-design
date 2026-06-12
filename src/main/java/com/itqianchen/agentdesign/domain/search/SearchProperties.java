package com.itqianchen.agentdesign.domain.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索索引和混合排序的配置属性。
 *
 * <p>外部请求可以覆盖部分检索参数，所有入口都应通过这里统一夹紧边界。</p>
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
     * 返回请求级 topK 的安全取值。
     *
     * @param requestedTopK 请求中指定的 topK；为空时使用配置默认值
     * @return 夹紧到 1 到 50 之间的 topK
     */
    public int normalizedTopK(Integer requestedTopK) {
        int value = requestedTopK == null ? topK : requestedTopK;
        return Math.clamp(value, 1, 50);
    }

    /**
     * 计算混合检索候选集大小。
     *
     * <p>候选集必须至少覆盖 topK，同时满足配置的最小候选数量，避免 RRF 融合时样本过少。</p>
     *
     * @param topK 已归一化的最终返回数量
     * @return 混合检索候选数量
     */
    public int hybridCandidateLimit(int topK) {
        return Math.max(topK * Math.max(1, hybridCandidateMultiplier), Math.max(1, hybridMinCandidates));
    }

    /**
     * 返回 RRF 排序常数的安全取值。
     *
     * @return 至少为 1 的 RRF k 值
     */
    public int normalizedRrfK() {
        return Math.max(1, rrfK);
    }
}


