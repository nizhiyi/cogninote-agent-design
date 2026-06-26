package com.itqianchen.agentdesign.domain.properties.search;

import com.itqianchen.agentdesign.domain.support.model.ModelConfigDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding 索引配置属性。
 *
 * <p>批量大小会影响 provider 限流和本地内存占用，读取后必须通过归一化方法获得安全范围。</p>
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        int dimensions,
        int batchSize,
        int requestsPerMinute,
        int tokensPerMinute
) {
    /**
     * 返回安全范围内的 embedding 批量大小。
     *
     * @return 夹紧到 1 到 128 之间的批量大小
     */
    public int normalizedBatchSize() {
        int value = batchSize <= 0 ? ModelConfigDefaults.EMBEDDING_BATCH_SIZE : batchSize;
        return Math.clamp(
                value,
                ModelConfigDefaults.MIN_EMBEDDING_BATCH_SIZE,
                ModelConfigDefaults.MAX_EMBEDDING_BATCH_SIZE
        );
    }

    /**
     * 返回安全范围内的每分钟请求数。
     *
     * @return 夹紧后的 RPM
     */
    public int normalizedRequestsPerMinute() {
        int value = requestsPerMinute <= 0
                ? ModelConfigDefaults.EMBEDDING_REQUESTS_PER_MINUTE
                : requestsPerMinute;
        return Math.clamp(
                value,
                ModelConfigDefaults.MIN_EMBEDDING_REQUESTS_PER_MINUTE,
                ModelConfigDefaults.MAX_EMBEDDING_REQUESTS_PER_MINUTE
        );
    }

    /**
     * 返回安全范围内的每分钟输入 token 数。
     *
     * @return 夹紧后的 TPM
     */
    public int normalizedTokensPerMinute() {
        int value = tokensPerMinute <= 0
                ? ModelConfigDefaults.EMBEDDING_TOKENS_PER_MINUTE
                : tokensPerMinute;
        return Math.clamp(
                value,
                ModelConfigDefaults.MIN_EMBEDDING_TOKENS_PER_MINUTE,
                ModelConfigDefaults.MAX_EMBEDDING_TOKENS_PER_MINUTE
        );
    }
}


