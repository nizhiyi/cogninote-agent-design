package com.itqianchen.agentdesign.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Model 配置 服务 测试 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-model-config",
        "app.storage.database-path=target/test-cogninote-model-config/cogninote.db",
        "server.address=127.0.0.1"
})
class ModelConfigServiceTests {

    @Autowired
    private ModelConfigService modelConfigService;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    /**
     * 清理 clear Database 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clearModelConfigs();
    }

    /**
     * 执行 模型配置 中的 active Defaults Are Split By Role 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void activeDefaultsAreSplitByRole() {
        ModelConfig chat = modelConfigService.activeChatOrDefault();
        ModelConfig embedding = modelConfigService.activeEmbeddingOrDefault();

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.role()).isEqualTo(ModelConfigRole.CHAT);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.provider()).isEqualTo(ModelProvider.DASHSCOPE);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.baseUrl()).isEqualTo(ModelConfigDefaults.BASE_URL);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.modelName()).isEqualTo("qwen-plus");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.resolvedTemperature()).isEqualTo(0.7);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.resolvedDefaultTopK()).isEqualTo(8);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.resolvedContextWindowTokens()).isEqualTo(ModelConfigDefaults.CONTEXT_WINDOW_TOKENS);

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(embedding.role()).isEqualTo(ModelConfigRole.EMBEDDING);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(embedding.modelName()).isEqualTo("text-embedding-v4");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(embedding.resolvedEmbeddingDimensions()).isEqualTo(1024);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(embedding.contextWindowTokens()).isNull();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(embedding.resolvedContextWindowTokens()).isZero();
    }

    /**
     * 创建 create And Activate Chat Does Not Overwrite Embedding 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @Test
    void createAndActivateChatDoesNotOverwriteEmbedding() {
        ModelConfig embedding = modelConfigService.create(embeddingRequest(
                "DASHSCOPE",
                "Embedding A",
                "sk-embedding",
                ModelConfigDefaults.BASE_URL,
                "text-embedding-v4",
                1024
        ));

        ModelConfig chat = modelConfigService.create(chatRequest(
                "OPENAI_COMPATIBLE",
                "Chat A",
                "sk-chat",
                "https://api.example.test/v1/chat/completions",
                "gpt-4.1-mini",
                0.3,
                12
        ));
        modelConfigService.activate(chat.id());

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.requireActiveChatConfigured().modelName()).isEqualTo("gpt-4.1-mini");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.requireActiveEmbeddingConfigured().modelName()).isEqualTo("text-embedding-v4");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.requireActiveEmbeddingConfigured().apiKey()).isEqualTo(embedding.apiKey());
    }

    /**
     * 执行 模型配置 中的 open Ai Compatible Provider Persists Custom Base Url Per Role 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void openAiCompatibleProviderPersistsCustomBaseUrlPerRole() {
        ModelConfig chat = modelConfigService.create(chatRequest(
                "OPENAI_COMPATIBLE",
                "Chat A",
                "sk-chat",
                "https://api.example.test/v1/chat/completions",
                "gpt-4.1-mini",
                0.4,
                10
        ));

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.provider()).isEqualTo(ModelProvider.OPENAI_COMPATIBLE);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.baseUrl()).isEqualTo("https://api.example.test/v1");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.modelName()).isEqualTo("gpt-4.1-mini");
    }

    /**
     * 执行 模型配置 中的 chat Context Window Can Be Customized And Returned In Settings 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chatContextWindowCanBeCustomizedAndReturnedInSettings() {
        ModelConfig chat = modelConfigService.create(chatRequest(
                "DASHSCOPE",
                "Chat 64K",
                "sk-test",
                ModelConfigDefaults.BASE_URL,
                "qwen-plus",
                0.7,
                8,
                64_000
        ));

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chat.contextWindowTokens()).isEqualTo(64_000);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.activeChatOrDefault().resolvedContextWindowTokens()).isEqualTo(64_000);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.settingsSnapshot(ModelConfigRole.CHAT).selectedConfig().contextWindowTokens())
                .isEqualTo(64_000);
    }

    /**
     * 执行 模型配置 中的 embedding 配置 Keeps Context Window Empty 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void embeddingConfigKeepsContextWindowEmpty() {
        ModelConfig embedding = modelConfigService.create(embeddingRequest(
                "DASHSCOPE",
                "Embedding A",
                "sk-embedding",
                ModelConfigDefaults.BASE_URL,
                "text-embedding-v4",
                1024
        ));

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(embedding.contextWindowTokens()).isNull();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.settingsSnapshot(ModelConfigRole.EMBEDDING)
                .selectedConfig()
                .contextWindowTokens()).isNull();
    }

    /**
     * 更新 update With Blank Api Key Keeps Existing Secret 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Test
    void updateWithBlankApiKeyKeepsExistingSecret() {
        ModelConfig saved = modelConfigService.create(chatRequest(
                "DASHSCOPE",
                "Chat A",
                "sk-test",
                ModelConfigDefaults.BASE_URL,
                "qwen-plus",
                0.7,
                8
        ));

        modelConfigService.update(saved.id(), chatRequest(
                "DASHSCOPE",
                "Chat A",
                "",
                ModelConfigDefaults.BASE_URL,
                "qwen-max",
                0.2,
                6
        ));

        ModelConfig config = modelConfigService.requireActiveChatConfigured();

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(config.apiKey()).isEqualTo("sk-test");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(config.modelName()).isEqualTo("qwen-max");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(config.resolvedTemperature()).isEqualTo(0.2);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(config.resolvedDefaultTopK()).isEqualTo(6);
    }

    /**
     * 删除 delete Only Active 配置 Creates Default Fallback 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Test
    void deleteOnlyActiveConfigCreatesDefaultFallback() {
        ModelConfig chat = modelConfigService.create(chatRequest(
                "DASHSCOPE",
                "Chat A",
                "sk-test",
                ModelConfigDefaults.BASE_URL,
                "qwen-plus",
                0.7,
                8
        ));

        modelConfigService.delete(chat.id());

        ModelConfig fallback = modelConfigService.activeChatOrDefault();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(fallback.active()).isTrue();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(fallback.role()).isEqualTo(ModelConfigRole.CHAT);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(fallback.modelName()).isEqualTo(ModelConfigDefaults.CHAT_MODEL);
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(fallback.hasApiKey()).isFalse();
    }

    /**
     * 删除 delete Active 配置 Promotes Remaining 配置 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Test
    void deleteActiveConfigPromotesRemainingConfig() {
        ModelConfig active = modelConfigService.create(chatRequest(
                "DASHSCOPE",
                "Chat A",
                "sk-active",
                ModelConfigDefaults.BASE_URL,
                "qwen-plus",
                0.7,
                8
        ));
        ModelConfig standby = modelConfigService.create(chatRequest(
                "DASHSCOPE",
                "Chat B",
                "sk-standby",
                ModelConfigDefaults.BASE_URL,
                "qwen-max",
                0.4,
                6
        ));

        modelConfigService.delete(active.id());

        ModelConfig promoted = modelConfigService.requireActiveChatConfigured();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(promoted.id()).isEqualTo(standby.id());
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(promoted.active()).isTrue();
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(promoted.modelName()).isEqualTo("qwen-max");
    }

    /**
     * 更新 save Legacy 请求 Splits Chat And Embedding 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Test
    void saveLegacyRequestSplitsChatAndEmbedding() {
        modelConfigService.save(new ModelConfigRequest(
                null,
                "DASHSCOPE",
                "DashScope",
                ModelConfigDefaults.BASE_URL,
                "sk-test",
                null,
                "qwen-max",
                "text-embedding-v4",
                1024,
                0.3,
                12,
                null,
                ModelConfigDefaults.CONTEXT_WINDOW_TOKENS
        ));

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.requireActiveChatConfigured().modelName()).isEqualTo("qwen-max");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(modelConfigService.requireActiveEmbeddingConfigured().modelName()).isEqualTo("text-embedding-v4");
    }

    /**
     * 更新 save Rejects Invalid Base Url 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Test
    void saveRejectsInvalidBaseUrl() {
        assertThatThrownBy(() -> modelConfigService.create(chatRequest("OPENAI_COMPATIBLE", "Chat A", "sk-test",
                "ftp://example.com", "qwen-plus", 0.7, 8)))
                .isInstanceOf(ModelConfigurationException.class)
                .hasMessageContaining("Base URL");
    }

    /**
     * 读取必需的 require Configured Fails Without Api Key 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    @Test
    void requireConfiguredFailsWithoutApiKey() {
        /**
         * 执行 模型配置 中的 assert That Thrown By 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThatThrownBy(() -> modelConfigService.requireActiveChatConfigured())
                .isInstanceOf(ModelConfigurationException.class)
                .hasMessageContaining("API Key");
    }

    /**
     * 执行 模型配置 中的 chat 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfigRequest chatRequest(
            String provider,
            String displayName,
            String apiKey,
            String baseUrl,
            String modelName,
            double temperature,
            int topK
    ) {
        return chatRequest(provider, displayName, apiKey, baseUrl, modelName, temperature, topK,
                ModelConfigDefaults.CONTEXT_WINDOW_TOKENS);
    }

    /**
     * 执行 模型配置 中的 chat 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfigRequest chatRequest(
            String provider,
            String displayName,
            String apiKey,
            String baseUrl,
            String modelName,
            double temperature,
            int topK,
            int contextWindowTokens
    ) {
        return new ModelConfigRequest(
                ModelConfigRole.CHAT.name(),
                provider,
                displayName,
                baseUrl,
                apiKey,
                modelName,
                modelName,
                null,
                null,
                temperature,
                topK,
                topK,
                contextWindowTokens
        );
    }

    /**
     * 执行 模型配置 中的 embedding 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfigRequest embeddingRequest(
            String provider,
            String displayName,
            String apiKey,
            String baseUrl,
            String modelName,
            int dimensions
    ) {
        return new ModelConfigRequest(
                ModelConfigRole.EMBEDDING.name(),
                provider,
                displayName,
                baseUrl,
                apiKey,
                modelName,
                null,
                modelName,
                dimensions,
                null,
                null,
                null,
                null
        );
    }
}
