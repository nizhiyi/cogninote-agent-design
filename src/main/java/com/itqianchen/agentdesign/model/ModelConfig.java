package com.itqianchen.agentdesign.model;

public record ModelConfig(
        String id,
        ModelProvider provider,
        String apiKey,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        double temperature,
        int topK,
        long createdAt,
        long updatedAt
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
