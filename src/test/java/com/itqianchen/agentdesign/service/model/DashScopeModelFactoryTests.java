package com.itqianchen.agentdesign.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

/**
 * Dash Scope Model 工厂 测试 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class DashScopeModelFactoryTests {

    /**
     * 执行 模型配置 中的 chat Endpoint Routes Traditional Qwen Models To Text Generation 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chatEndpointRoutesTraditionalQwenModelsToTextGeneration() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen-plus").multiModel())
                .isFalse();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen-max").multiModel())
                .isFalse();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3-max").multiModel())
                .isFalse();
    }

    /**
     * 执行 模型配置 中的 chat Endpoint Routes New Dash Scope Multimodal Models To Multimodal Generation 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chatEndpointRoutesNewDashScopeMultimodalModelsToMultimodalGeneration() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3.6-plus").multiModel())
                .isTrue();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3.7-plus").multiModel())
                .isTrue();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3.5-omni-plus").multiModel())
                .isTrue();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3-vl-plus").multiModel())
                .isTrue();
    }

    /**
     * 执行 模型配置 中的 embedding Model Cache Separates Document And Query Text Type 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void embeddingModelCacheSeparatesDocumentAndQueryTextType() {
        DashScopeModelFactory factory = new DashScopeModelFactory(observationRegistryProvider());
        ModelConfig config = embeddingConfig();

        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        EmbeddingModel documentModel = factory.embeddingModel(config, "document");
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        EmbeddingModel sameDocumentModel = factory.embeddingModel(config, "document");
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        EmbeddingModel queryModel = factory.embeddingModel(config, "query");

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(sameDocumentModel).isSameAs(documentModel);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(queryModel).isNotSameAs(documentModel);
    }

    /**
     * 执行 模型配置 中的 observation 注册表 Provider 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private ObjectProvider<ObservationRegistry> observationRegistryProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);
        /**
         * 执行 模型配置 中的 when 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        when(provider.getIfAvailable(any())).thenReturn(ObservationRegistry.NOOP);
        return provider;
    }

    /**
     * 执行 模型配置 中的 embedding 配置 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private ModelConfig embeddingConfig() {
        return new ModelConfig(
                "embedding",
                ModelConfigRole.EMBEDDING,
                ModelProvider.DASHSCOPE,
                "DashScope Embedding",
                ModelConfigDefaults.BASE_URL,
                "test-api-key",
                ModelConfigDefaults.EMBEDDING_MODEL,
                ModelConfigDefaults.EMBEDDING_DIMENSIONS,
                null,
                null,
                null,
                true,
                1L,
                1L
        );
    }
}
