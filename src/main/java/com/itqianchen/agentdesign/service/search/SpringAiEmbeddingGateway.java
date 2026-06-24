package com.itqianchen.agentdesign.service.search;


import com.itqianchen.agentdesign.domain.enums.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.entity.model.ModelConfig;
import com.itqianchen.agentdesign.domain.enums.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.interfaces.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.interfaces.search.EmbeddingGateway;
import com.itqianchen.agentdesign.domain.properties.search.EmbeddingProperties;
import com.itqianchen.agentdesign.domain.exception.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.repository.model.ModelConfigRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Embedding 模型的运行时适配器。
 *
 * <p>优先使用数据库中的 active EMBEDDING 配置；没有配置时才回退到 Spring Boot 自动装配模型。
 * 不可用时由检索层降级到关键词检索，而不是阻止应用启动。</p>
 */
@Component
public class SpringAiEmbeddingGateway implements EmbeddingGateway {

    private final Optional<EmbeddingModel> embeddingModel;
    private final ModelConfigRepository modelConfigRepository;
    private final AiRuntimeFactory aiRuntimeFactory;
    private final EmbeddingProperties embeddingProperties;
    private final String embeddingProvider;
    private final String dashscopeApiKey;

    /**
     * 注入 Embedding 运行时依赖。
     *
     * @param embeddingModel Spring Boot 自动配置的 EmbeddingModel
     * @param modelConfigRepository 模型配置仓储
     * @param aiRuntimeFactory AI 运行时工厂
     * @param embeddingProperties Embedding 配置
     * @param embeddingProvider 自动配置 Provider 名称
     * @param dashscopeApiKey DashScope 自动配置 API Key
     */
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

    /**
     * 判断当前是否有可用 Embedding 能力。
     *
     * @return 是否可生成向量
     */
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

    /**
     * 返回当前 Embedding 维度。
     *
     * @return 向量维度
     */
    @Override
    public int dimensions() {
        return modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey)
                .map(ModelConfig::resolvedEmbeddingDimensions)
                .orElse(embeddingProperties.dimensions());
    }

    /**
     * 为文档 chunk 生成向量。
     *
     * @param texts 文档 chunk 文本
     * @return 向量列表
     */
    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embedTexts(texts, EmbeddingPurpose.DOCUMENT);
    }

    /**
     * 为检索 query 生成向量。
     *
     * @param query 检索 query
     * @return query 向量
     */
    @Override
    public float[] embedQuery(String query) {
        List<float[]> vectors = embedTexts(List.of(query), EmbeddingPurpose.QUERY);
        return vectors.getFirst();
    }

    /**
     * 批量生成向量并校验维度。
     *
     * @param texts 待生成向量的文本
     * @param purpose query 或 document 场景
     * @return 向量列表
     */
    private List<float[]> embedTexts(List<String> texts, EmbeddingPurpose purpose) {
        if (!isAvailable()) {
            throw new EmbeddingUnavailableException("Embedding model is not configured");
        }

        int expectedDimensions = dimensions();

        List<float[]> vectors = new ArrayList<>();
        int batchSize = embeddingProperties.normalizedBatchSize();
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, texts.size());
            vectors.addAll(embedSlice(texts.subList(start, end), purpose));
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

    /**
     * 读取自动配置的 EmbeddingModel。
     *
     * @return EmbeddingModel
     */
    private EmbeddingModel activeEmbeddingModel() {
        return embeddingModel.orElseThrow(() ->
                new EmbeddingUnavailableException("Embedding model is not configured"));
    }

    /**
     * 按批次生成向量。
     *
     * @param texts 当前批次文本
     * @param purpose query 或 document 场景
     * @return 当前批次向量
     */
    private List<float[]> embedSlice(List<String> texts, EmbeddingPurpose purpose) {
        return modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey)
                .map(config -> embedConfigured(config, texts, purpose))
                .orElseGet(() -> embedAutoConfigured(texts, purpose));
    }

    /**
     * 使用数据库中激活的 Embedding 配置生成向量。
     *
     * @param config Embedding 配置
     * @param texts 文本列表
     * @param purpose query 或 document 场景
     * @return 向量列表
     */
    private List<float[]> embedConfigured(ModelConfig config, List<String> texts, EmbeddingPurpose purpose) {
        if (purpose == EmbeddingPurpose.QUERY) {
            return List.of(aiRuntimeFactory.embeddingRuntime(config).embedQuery(texts.getFirst()));
        }
        return aiRuntimeFactory.embeddingRuntime(config).embedDocuments(texts);
    }

    /**
     * 使用 Spring Boot 自动配置模型生成向量。
     *
     * @param texts 文本列表
     * @param purpose query 或 document 场景
     * @return 向量列表
     */
    private List<float[]> embedAutoConfigured(List<String> texts, EmbeddingPurpose purpose) {
        EmbeddingModel model = activeEmbeddingModel();
        if (purpose == EmbeddingPurpose.QUERY) {
            return List.of(model.embed(texts.getFirst()));
        }
        return model.embed(texts);
    }

    private enum EmbeddingPurpose {
        DOCUMENT,
        QUERY
    }
}


