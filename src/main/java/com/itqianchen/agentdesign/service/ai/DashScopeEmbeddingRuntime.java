package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

final class DashScopeEmbeddingRuntime implements AiEmbeddingRuntime {

    private static final String DOCUMENT_TEXT_TYPE = "document";
    private static final String QUERY_TEXT_TYPE = "query";

    private final DashScopeModelFactory dashScopeModelFactory;
    private final ModelConfig config;

    DashScopeEmbeddingRuntime(DashScopeModelFactory dashScopeModelFactory, ModelConfig config) {
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.config = config;
    }

    @Override
    public float[] embedQuery(String query) {
        return embeddingModel(QUERY_TEXT_TYPE).embed(query);
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embeddingModel(DOCUMENT_TEXT_TYPE).embed(texts);
    }

    private EmbeddingModel embeddingModel(String textType) {
        return dashScopeModelFactory.embeddingModel(config, textType);
    }
}
