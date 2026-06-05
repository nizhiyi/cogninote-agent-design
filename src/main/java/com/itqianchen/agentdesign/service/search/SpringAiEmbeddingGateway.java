package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.search.EmbeddingGateway;
import com.itqianchen.agentdesign.domain.search.EmbeddingProperties;
import com.itqianchen.agentdesign.domain.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.repository.model.ModelConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SpringAiEmbeddingGateway implements EmbeddingGateway {

    private final Optional<EmbeddingModel> embeddingModel;
    private final ModelConfigRepository modelConfigRepository;
    private final AiRuntimeFactory aiRuntimeFactory;
    private final EmbeddingProperties embeddingProperties;
    private final String embeddingProvider;
    private final String dashscopeApiKey;

    public SpringAiEmbeddingGateway(
            Optional<EmbeddingModel> embeddingModel,
            ModelConfigRepository modelConfigRepository,
            AiRuntimeFactory aiRuntimeFactory,
            EmbeddingProperties embeddingProperties,
            @Value("${spring.ai.model.embedding:none}") String embeddingProvider,
            @Value("${spring.ai.dashscope.api-key:}") String dashscopeApiKey
    ) {
        this.embeddingModel = embeddingModel;
        this.modelConfigRepository = modelConfigRepository;
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.embeddingProperties = embeddingProperties;
        this.embeddingProvider = embeddingProvider;
        this.dashscopeApiKey = dashscopeApiKey;
    }

    @Override
    public boolean isAvailable() {
        Optional<ModelConfig> configuredModel = modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey);
        if (configuredModel.isPresent()) {
            return true;
        }

        if (embeddingModel.isEmpty() || "none".equalsIgnoreCase(embeddingProvider)) {
            return false;
        }

        // DashScope starter 在依赖中存在时也不能强制要求 API Key。
        // 未配置密钥时保持应用可启动，由检索层按需降级到关键词检索。
        return !"dashscope".equalsIgnoreCase(embeddingProvider) || StringUtils.hasText(dashscopeApiKey);
    }

    @Override
    public int dimensions() {
        return modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey)
                .map(ModelConfig::resolvedEmbeddingDimensions)
                .orElse(embeddingProperties.dimensions());
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (!isAvailable()) {
            throw new EmbeddingUnavailableException("Embedding model is not configured");
        }

        int expectedDimensions = dimensions();

        List<float[]> vectors = new ArrayList<>();
        int batchSize = embeddingProperties.normalizedBatchSize();
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            vectors.addAll(embedSlice(texts.subList(start, end)));
        }

        for (float[] vector : vectors) {
            if (vector.length != expectedDimensions) {
                throw new EmbeddingUnavailableException(
                        "Embedding dimensions mismatch: expected "
                                + expectedDimensions
                                + " but got "
                                + vector.length
                );
            }
        }

        return vectors;
    }

    private EmbeddingModel activeEmbeddingModel() {
        return embeddingModel.orElseThrow(() ->
                new EmbeddingUnavailableException("Embedding model is not configured"));
    }

    private List<float[]> embedSlice(List<String> texts) {
        return modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey)
                .map(config -> embedConfigured(config, texts))
                .orElseGet(() -> activeEmbeddingModel().embed(texts));
    }

    private List<float[]> embedConfigured(ModelConfig config, List<String> texts) {
        return aiRuntimeFactory.embeddingRuntime(config).embedBatch(texts);
    }
}


