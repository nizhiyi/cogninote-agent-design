package com.itqianchen.agentdesign.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.itqianchen.agentdesign.domain.model.ModelCapability;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.dto.model.ModelOptionResponse;
import com.itqianchen.agentdesign.dto.model.ModelOptionsResponse;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Model Catalog 服务 承载 模型配置 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class ModelCatalogService {

    private final ModelConfigService modelConfigService;
    // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
    private final RestClient restClient;

    /**
     * 注入 ModelCatalogService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ModelCatalogService(ModelConfigService modelConfigService, RestClient.Builder restClientBuilder) {
        this.modelConfigService = modelConfigService;
        // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
        this.restClient = restClientBuilder.build();
    }

    /**
     * 拉取 fetch Models 数据。
     * <p>外部 HTTP 或模型提供商响应会在这里转换为本地 DTO。</p>
     */
    public ModelOptionsResponse fetchModels(ModelConfigRequest request) {
        ModelConfig config = modelConfigService.connectionTestConfig(request);

        try {
            // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
            JsonNode response = restClient.get()
                    .uri(modelsUri(config))
                    .header("Authorization", "Bearer " + config.apiKey())
                    // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
                    .retrieve()
                    .body(JsonNode.class);
            return new ModelOptionsResponse(parseModels(response), System.currentTimeMillis());
        } catch (RestClientException ex) {
            throw new ModelConfigurationException("Failed to fetch model list: " + ex.getMessage(), ex);
        }
    }

    /**
     * 执行 模型配置 中的 models Uri 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static java.net.URI modelsUri(ModelConfig config) {
        return switch (config.provider()) {
            case DASHSCOPE -> DashScopeBaseUrls.modelsUri(config.baseUrl());
            case OPENAI_COMPATIBLE -> OpenAiCompatibleUrls.modelsUri(config.baseUrl());
        };
    }

    /**
     * 解析 parse Models 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    private static List<ModelOptionResponse> parseModels(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray()) {
            throw new ModelConfigurationException("Model list response does not contain data[]");
        }

        return StreamSupport.stream(data.spliterator(), false)
                .map(ModelCatalogService::toModelOption)
                .filter(option -> option.id() != null && !option.id().isBlank())
                .sorted(Comparator.comparing(ModelOptionResponse::capability)
                        .thenComparing(ModelOptionResponse::id))
                .toList();
    }

    /**
     * 执行 模型配置 中的 to Model Option 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelOptionResponse toModelOption(JsonNode node) {
        String id = text(node, "id");
        String name = text(node, "name");
        if (name.isBlank()) {
            name = id;
        }
        return new ModelOptionResponse(id, name, classify(id));
    }

    /**
     * 执行 模型配置 中的 text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    /**
     * 将输入映射为 classify 对应的业务分类。
     * <p>分类规则集中维护，避免调用方散落字符串或枚举判断。</p>
     */
    private static ModelCapability classify(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return ModelCapability.UNKNOWN;
        }
        String normalized = modelId.toLowerCase(Locale.ROOT);
        if (normalized.contains("embedding") || normalized.contains("embed")) {
            return ModelCapability.EMBEDDING;
        }
        // DashScope/OpenAI compatible 的 /models 通常不给能力字段。
        // 当前阶段宁可把未知文本模型默认归为 Chat，避免用户拿不到可选项。
        return ModelCapability.CHAT;
    }
}
