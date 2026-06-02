package com.itqianchen.agentdesign.model;

public record ModelConfigResponse(
        String provider,
        boolean apiKeyConfigured,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        double temperature,
        int topK,
        Long updatedAt
) {
    public static ModelConfigResponse from(ModelConfig config) {
        return new ModelConfigResponse(
                config.provider().name(),
                config.hasApiKey(),
                config.chatModel(),
                config.embeddingModel(),
                config.embeddingDimensions(),
                config.temperature(),
                config.topK(),
                config.updatedAt()
        );
    }
}
