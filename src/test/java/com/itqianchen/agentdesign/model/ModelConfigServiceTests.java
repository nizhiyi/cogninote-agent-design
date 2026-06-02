package com.itqianchen.agentdesign.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM model_config");
    }

    @Test
    void activeOrDefaultReturnsDashScopeDefaults() {
        ModelConfig config = modelConfigService.activeOrDefault();

        assertThat(config.provider()).isEqualTo(ModelProvider.DASHSCOPE);
        assertThat(config.displayName()).isEqualTo(ModelConfigDefaults.DISPLAY_NAME);
        assertThat(config.baseUrl()).isEqualTo(ModelConfigDefaults.BASE_URL);
        assertThat(config.chatModel()).isEqualTo("qwen-plus");
        assertThat(config.embeddingModel()).isEqualTo("text-embedding-v4");
        assertThat(config.embeddingDimensions()).isEqualTo(1024);
        assertThat(config.temperature()).isEqualTo(0.7);
        assertThat(config.topK()).isEqualTo(8);
        assertThat(config.hasApiKey()).isFalse();
    }

    @Test
    void savePersistsActiveConfig() {
        modelConfigService.save(request(
                "DASHSCOPE",
                "sk-test",
                "https://dashscope.aliyuncs.com/api/v1/",
                "qwen-max",
                "text-embedding-v4",
                0.3,
                12
        ));

        ModelConfig config = modelConfigService.requireConfigured();

        assertThat(config.apiKey()).isEqualTo("sk-test");
        assertThat(config.baseUrl()).isEqualTo(ModelConfigDefaults.BASE_URL);
        assertThat(config.chatModel()).isEqualTo("qwen-max");
        assertThat(config.temperature()).isEqualTo(0.3);
        assertThat(config.topK()).isEqualTo(12);
    }

    @Test
    void dashScopeProviderIgnoresCustomBaseUrlAndKeepsOfficialDefault() {
        modelConfigService.save(request(
                "DASHSCOPE",
                "sk-test",
                "https://api.example.test/v1",
                "qwen-plus",
                "text-embedding-v4",
                0.7,
                8
        ));

        ModelConfig config = modelConfigService.requireConfigured();

        assertThat(config.provider()).isEqualTo(ModelProvider.DASHSCOPE);
        assertThat(config.baseUrl()).isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    @Test
    void dashScopeProviderUsesDefaultWhenBaseUrlIsBlank() {
        modelConfigService.save(request(
                "DASHSCOPE",
                "sk-test",
                "",
                "qwen-plus",
                "text-embedding-v4",
                0.7,
                8
        ));

        ModelConfig config = modelConfigService.requireConfigured();

        assertThat(config.provider()).isEqualTo(ModelProvider.DASHSCOPE);
        assertThat(config.baseUrl()).isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    @Test
    void openAiCompatibleProviderPersistsCustomBaseUrl() {
        modelConfigService.save(request(
                "OPENAI_COMPATIBLE",
                "sk-test",
                "https://api.example.test/v1/chat/completions",
                "gpt-4.1-mini",
                "text-embedding-3-small",
                0.4,
                10
        ));

        ModelConfig config = modelConfigService.requireConfigured();

        assertThat(config.provider()).isEqualTo(ModelProvider.OPENAI_COMPATIBLE);
        assertThat(config.baseUrl()).isEqualTo("https://api.example.test/v1");
        assertThat(config.chatModel()).isEqualTo("gpt-4.1-mini");
        assertThat(config.embeddingModel()).isEqualTo("text-embedding-3-small");
    }

    @Test
    void saveWithBlankApiKeyKeepsExistingSecret() {
        modelConfigService.save(request("DASHSCOPE", "sk-test", ModelConfigDefaults.BASE_URL, "qwen-plus",
                "text-embedding-v4", 0.7, 8));

        modelConfigService.save(request("DASHSCOPE", "", ModelConfigDefaults.BASE_URL, "qwen-max",
                "text-embedding-v4", 0.2, 6));

        ModelConfig config = modelConfigService.requireConfigured();

        assertThat(config.apiKey()).isEqualTo("sk-test");
        assertThat(config.chatModel()).isEqualTo("qwen-max");
        assertThat(config.temperature()).isEqualTo(0.2);
        assertThat(config.topK()).isEqualTo(6);
    }

    @Test
    void saveRejectsInvalidBaseUrl() {
        assertThatThrownBy(() -> modelConfigService.save(request("OPENAI_COMPATIBLE", "sk-test", "ftp://example.com",
                "qwen-plus", "text-embedding-v4", 0.7, 8)))
                .isInstanceOf(ModelConfigurationException.class)
                .hasMessageContaining("Base URL");
    }

    @Test
    void requireConfiguredFailsWithoutApiKey() {
        assertThatThrownBy(() -> modelConfigService.requireConfigured())
                .isInstanceOf(ModelConfigurationException.class)
                .hasMessageContaining("API Key");
    }

    private static ModelConfigRequest request(
            String provider,
            String apiKey,
            String baseUrl,
            String chatModel,
            String embeddingModel,
            double temperature,
            int topK
    ) {
        return new ModelConfigRequest(
                provider,
                "DashScope",
                baseUrl,
                apiKey,
                chatModel,
                embeddingModel,
                1024,
                temperature,
                topK
        );
    }
}
