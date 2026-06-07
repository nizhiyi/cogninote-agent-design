package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

final class SpringAiEmbeddingRuntime implements AiEmbeddingRuntime {

    private final EmbeddingModel embeddingModel;

    SpringAiEmbeddingRuntime(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embedQuery(String query) {
        return embeddingModel.embed(query);
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embeddingModel.embed(texts);
    }
}
