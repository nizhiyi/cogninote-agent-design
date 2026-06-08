package com.itqianchen.agentdesign.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.itqianchen.agentdesign.domain.model.ModelCapability;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.dto.model.ModelOptionsResponse;
import com.itqianchen.agentdesign.repository.model.ModelConfigRepository;
import com.itqianchen.agentdesign.service.model.ModelCatalogService;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Model Catalog 服务 测试 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class ModelCatalogServiceTests {

    /**
     * 拉取 fetch Models Uses Dash Scope Default Models Endpoint And Classifies Results 数据。
     * <p>外部 HTTP 或模型提供商响应会在这里转换为本地 DTO。</p>
     */
    @Test
    void fetchModelsUsesDashScopeDefaultModelsEndpointAndClassifiesResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ModelCatalogService service = new ModelCatalogService(
                new ModelConfigService(emptyRepository()),
                builder
        );

        server.expect(requestTo("https://dashscope.aliyuncs.com/compatible-mode/v1/models"))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andRespond(withSuccess("""
                        {
                          "object": "list",
                          "data": [
                            { "id": "qwen-plus" },
                            { "id": "text-embedding-v4" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ModelOptionsResponse response = service.fetchModels(new ModelConfigRequest(
                ModelConfigRole.CHAT.name(),
                "DASHSCOPE",
                "DashScope",
                // DashScope provider 固定使用百炼默认地址，这里传入自定义 host 是为了防止旧逻辑回退。
                "https://example.test/compatible-mode/v1",
                "sk-test",
                "qwen-plus",
                "qwen-plus",
                "text-embedding-v4",
                1024,
                0.7,
                8,
                8,
                ModelConfigDefaults.CONTEXT_WINDOW_TOKENS
        ));

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.models())
                .extracting("id", "capability")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("qwen-plus", ModelCapability.CHAT),
                        org.assertj.core.groups.Tuple.tuple("text-embedding-v4", ModelCapability.EMBEDDING)
                );
        server.verify();
    }

    /**
     * 拉取 fetch Models Uses Custom Open Ai Compatible Models Endpoint 数据。
     * <p>外部 HTTP 或模型提供商响应会在这里转换为本地 DTO。</p>
     */
    @Test
    void fetchModelsUsesCustomOpenAiCompatibleModelsEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ModelCatalogService service = new ModelCatalogService(
                new ModelConfigService(emptyRepository()),
                builder
        );

        server.expect(requestTo("https://api.example.test/v1/models"))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            { "id": "gpt-4.1-mini" },
                            { "id": "text-embedding-3-small" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ModelOptionsResponse response = service.fetchModels(new ModelConfigRequest(
                ModelConfigRole.CHAT.name(),
                "OPENAI_COMPATIBLE",
                "OpenAI-compatible",
                "https://api.example.test/v1/chat/completions",
                "sk-test",
                "gpt-4.1-mini",
                "gpt-4.1-mini",
                "text-embedding-3-small",
                1536,
                0.7,
                8,
                8,
                ModelConfigDefaults.CONTEXT_WINDOW_TOKENS
        ));

        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.models())
                .extracting("id", "capability")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("gpt-4.1-mini", ModelCapability.CHAT),
                        org.assertj.core.groups.Tuple.tuple("text-embedding-3-small", ModelCapability.EMBEDDING)
                );
        server.verify();
    }

    /**
     * 执行 模型配置 中的 empty 仓储 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfigRepository emptyRepository() {
        return new ModelConfigRepository(null) {
            /**
             * 读取 find Active 对应的数据。
             * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
             */
            @Override
            public java.util.Optional<com.itqianchen.agentdesign.domain.model.ModelConfig> findActive(ModelConfigRole role) {
                return Optional.empty();
            }
        };
    }
}
