package com.itqianchen.agentdesign.domain.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 索引配置属性。
 *
 * <p>批量大小会影响 provider 限流和本地内存占用，读取后必须通过归一化方法获得安全范围。</p>
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        int dimensions,
        int batchSize
) {
    /**
     * 返回安全范围内的 embedding 批量大小。
     *
     * @return 夹紧到 1 到 128 之间的批量大小
     */
    public int normalizedBatchSize() {
        return Math.clamp(batchSize, 1, 128);
    }
}


