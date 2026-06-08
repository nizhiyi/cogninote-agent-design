package com.itqianchen.agentdesign.domain.model;

/**
 * Model 配置 集中维护 模型配置 相关的 Spring 配置。
 * <p>这里的 Bean 或扫描配置会影响应用启动阶段的基础设施装配。</p>
 */
public record ModelConfig(
        String id,
        ModelConfigRole role,
        ModelProvider provider,
        String displayName,
        String baseUrl,
        String apiKey,
        String modelName,
        Integer embeddingDimensions,
        Double temperature,
        Integer defaultTopK,
        Integer contextWindowTokens,
        boolean active,
        long createdAt,
        long updatedAt
) {
    /**
     * 判断 has Api Key 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 解析 resolved Embedding Dimensions 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public int resolvedEmbeddingDimensions() {
        return embeddingDimensions == null
                ? ModelConfigDefaults.EMBEDDING_DIMENSIONS
                : embeddingDimensions;
    }

    /**
     * 解析 resolved Temperature 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public double resolvedTemperature() {
        return temperature == null ? ModelConfigDefaults.TEMPERATURE : temperature;
    }

    /**
     * 解析 resolved Default Top K 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public int resolvedDefaultTopK() {
        return defaultTopK == null ? ModelConfigDefaults.TOP_K : defaultTopK;
    }

    /**
     * 解析 resolved Context Window Tokens 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    public int resolvedContextWindowTokens() {
        if (role != ModelConfigRole.CHAT) {
            return 0;
        }
        return contextWindowTokens == null
                ? ModelConfigDefaults.CONTEXT_WINDOW_TOKENS
                : Math.clamp(
                        contextWindowTokens,
                        ModelConfigDefaults.MIN_CONTEXT_WINDOW_TOKENS,
                        ModelConfigDefaults.MAX_CONTEXT_WINDOW_TOKENS
                );
    }
}


