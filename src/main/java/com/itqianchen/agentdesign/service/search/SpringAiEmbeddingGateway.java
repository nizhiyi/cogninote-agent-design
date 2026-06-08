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

/**
 * Spring Ai Embedding 网关 将基础设施能力适配为 检索索引 领域网关。
 * <p>领域层通过网关调用外部能力，避免直接耦合具体 SDK。</p>
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
     * 注入 SpringAiEmbeddingGateway 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
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
     * 判断 is Available 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    @Override
    public boolean isAvailable() {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
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
     * 执行 检索索引 中的 dimensions 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public int dimensions() {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey)
                .map(ModelConfig::resolvedEmbeddingDimensions)
                .orElse(embeddingProperties.dimensions());
    }

    /**
     * 执行 检索索引 中的 embed Documents 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embedTexts(texts, EmbeddingPurpose.DOCUMENT);
    }

    /**
     * 执行 检索索引 中的 embed Query 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public float[] embedQuery(String query) {
        List<float[]> vectors = embedTexts(List.of(query), EmbeddingPurpose.QUERY);
        return vectors.getFirst();
    }

    /**
     * 执行 检索索引 中的 embed Texts 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 执行 检索索引 中的 active Embedding Model 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private EmbeddingModel activeEmbeddingModel() {
        return embeddingModel.orElseThrow(() ->
                new EmbeddingUnavailableException("Embedding model is not configured"));
    }

    /**
     * 执行 检索索引 中的 embed Slice 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<float[]> embedSlice(List<String> texts, EmbeddingPurpose purpose) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.findActive(ModelConfigRole.EMBEDDING)
                .filter(ModelConfig::hasApiKey)
                .map(config -> embedConfigured(config, texts, purpose))
                .orElseGet(() -> embedAutoConfigured(texts, purpose));
    }

    /**
     * 执行 检索索引 中的 embed Configured 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<float[]> embedConfigured(ModelConfig config, List<String> texts, EmbeddingPurpose purpose) {
        if (purpose == EmbeddingPurpose.QUERY) {
            return List.of(aiRuntimeFactory.embeddingRuntime(config).embedQuery(texts.getFirst()));
        }
        return aiRuntimeFactory.embeddingRuntime(config).embedDocuments(texts);
    }

    /**
     * 执行 检索索引 中的 embed Auto Configured 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<float[]> embedAutoConfigured(List<String> texts, EmbeddingPurpose purpose) {
        EmbeddingModel model = activeEmbeddingModel();
        if (purpose == EmbeddingPurpose.QUERY) {
            return List.of(model.embed(texts.getFirst()));
        }
        return model.embed(texts);
    }

    /**
     * Embedding Purpose 枚举 检索索引 的稳定取值。
     * <p>枚举值可能进入数据库或 API 响应，修改时需要考虑兼容性。</p>
     */
    private enum EmbeddingPurpose {
        DOCUMENT,
        QUERY
    }
}


