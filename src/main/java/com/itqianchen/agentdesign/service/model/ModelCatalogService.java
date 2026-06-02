package com.itqianchen.agentdesign.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.itqianchen.agentdesign.domain.model.ModelCapability;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.dto.model.ModelOptionResponse;
import com.itqianchen.agentdesign.dto.model.ModelOptionsResponse;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ModelCatalogService {

    private static final String MODELS_PATH = "/models";

    private final ModelConfigService modelConfigService;
    private final RestClient restClient;

    public ModelCatalogService(ModelConfigService modelConfigService, RestClient.Builder restClientBuilder) {
        this.modelConfigService = modelConfigService;
        this.restClient = restClientBuilder.build();
    }

    public ModelOptionsResponse fetchModels(ModelConfigRequest request) {
        ModelConfig config = modelConfigService.connectionTestConfig(request);
        URI modelsUri = modelsUri(config.baseUrl());

        try {
            JsonNode response = restClient.get()
                    .uri(modelsUri)
                    .header("Authorization", "Bearer " + config.apiKey())
                    .retrieve()
                    .body(JsonNode.class);
            return new ModelOptionsResponse(parseModels(response), System.currentTimeMillis());
        } catch (RestClientException ex) {
            throw new ModelConfigurationException("Failed to fetch model list: " + ex.getMessage(), ex);
        }
    }

    private static URI modelsUri(String baseUrl) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBaseUrl + MODELS_PATH);
    }

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

    private static ModelOptionResponse toModelOption(JsonNode node) {
        String id = text(node, "id");
        String name = text(node, "name");
        if (name.isBlank()) {
            name = id;
        }
        return new ModelOptionResponse(id, name, classify(id));
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

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
