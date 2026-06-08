package com.itqianchen.agentdesign.domain.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 配置属性 映射 检索索引 的 YAML 配置。
 * <p>通过类型化配置隔离环境变量、默认值和业务代码。</p>
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        int dimensions,
        int batchSize
) {
    /**
     * 规范化 normalized Batch Size 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    public int normalizedBatchSize() {
        return Math.clamp(batchSize, 1, 128);
    }
}


