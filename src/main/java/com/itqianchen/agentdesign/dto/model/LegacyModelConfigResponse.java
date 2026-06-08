package com.itqianchen.agentdesign.dto.model;

import com.itqianchen.agentdesign.domain.model.ModelConfig;

/**
 * Legacy Model 配置 响应 定义返回给前端的 模型配置 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record LegacyModelConfigResponse(
        String provider,
        String displayName,
        String baseUrl,
        boolean apiKeyConfigured,
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        double temperature,
        int topK,
        int contextWindowTokens,
        Long updatedAt,
        ModelConfigResponse chat,
        ModelConfigResponse embedding
) {
    /**
     * 将领域对象转换为 LegacyModelConfigResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
    public static LegacyModelConfigResponse from(ModelConfig chat, ModelConfig embedding) {
        return new LegacyModelConfigResponse(
                chat.provider().name(),
                chat.displayName(),
                chat.baseUrl(),
                chat.hasApiKey(),
                chat.apiKey(),
                chat.modelName(),
                embedding.modelName(),
                embedding.resolvedEmbeddingDimensions(),
                chat.resolvedTemperature(),
                chat.resolvedDefaultTopK(),
                chat.resolvedContextWindowTokens(),
                Math.max(chat.updatedAt(), embedding.updatedAt()),
                ModelConfigResponse.from(chat),
                ModelConfigResponse.from(embedding)
        );
    }
}
