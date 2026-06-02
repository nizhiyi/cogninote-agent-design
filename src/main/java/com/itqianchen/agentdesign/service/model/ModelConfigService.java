package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.repository.model.ModelConfigRepository;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

    public ModelConfigService(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    public ModelConfig activeOrDefault() {
        long now = System.currentTimeMillis();
        return modelConfigRepository.findActive()
                .orElseGet(() -> new ModelConfig(
                        ModelConfigDefaults.ACTIVE_CONFIG_ID,
                        ModelConfigDefaults.PROVIDER,
                        ModelConfigDefaults.DISPLAY_NAME,
                        ModelConfigDefaults.BASE_URL,
                        "",
                        ModelConfigDefaults.CHAT_MODEL,
                        ModelConfigDefaults.EMBEDDING_MODEL,
                        ModelConfigDefaults.EMBEDDING_DIMENSIONS,
                        ModelConfigDefaults.TEMPERATURE,
                        ModelConfigDefaults.TOP_K,
                        now,
                        now
                ));
    }

    public ModelConfig requireConfigured() {
        ModelConfig config = activeOrDefault();
        if (!config.hasApiKey()) {
            throw new ModelConfigurationException("DashScope API Key is not configured");
        }
        return config;
    }

    @Transactional
    public ModelConfig save(ModelConfigRequest request) {
        ModelConfig existing = activeOrDefault();
        long now = System.currentTimeMillis();
        ModelConfig config = mergeRequest(request, existing, now);
        return modelConfigRepository.saveActive(config);
    }

    public ModelConfig connectionTestConfig(ModelConfigRequest request) {
        ModelConfig config = mergeRequest(request, activeOrDefault(), System.currentTimeMillis());
        if (!config.hasApiKey()) {
            throw new ModelConfigurationException("DashScope API Key is required for connection test");
        }
        return config;
    }

    private static String normalizeApiKey(String apiKey) {
        return apiKey == null ? "" : apiKey.trim();
    }

    private static ModelConfig mergeRequest(ModelConfigRequest request, ModelConfig existing, long now) {
        String requestedApiKey = normalizeApiKey(request.apiKey());
        // GET 响应不会回显已保存的 API Key。
        // 因此前端提交空 key 时表示“复用旧 key”，不是清空密钥。
        String apiKey = requestedApiKey.isBlank() ? existing.apiKey() : requestedApiKey;
        return new ModelConfig(
                ModelConfigDefaults.ACTIVE_CONFIG_ID,
                normalizeProvider(request.provider()),
                normalizeDisplayName(request.displayName()),
                normalizeBaseUrl(request.baseUrl()),
                apiKey,
                request.chatModel().trim(),
                request.embeddingModel().trim(),
                request.embeddingDimensions() == null
                        ? ModelConfigDefaults.EMBEDDING_DIMENSIONS
                        : request.embeddingDimensions(),
                request.temperature() == null
                        ? ModelConfigDefaults.TEMPERATURE
                        : request.temperature(),
                request.topK() == null
                        ? ModelConfigDefaults.TOP_K
                        : request.topK(),
                existing.createdAt(),
                now
        );
    }

    private static ModelProvider normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return ModelConfigDefaults.PROVIDER;
        }
        try {
            return ModelProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ModelConfigurationException("Unsupported model provider: " + provider);
        }
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return ModelConfigDefaults.DISPLAY_NAME;
        }
        return displayName.trim();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank()
                ? ModelConfigDefaults.BASE_URL
                : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        try {
            URI uri = new URI(normalized);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ModelConfigurationException("Base URL must use http or https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ModelConfigurationException("Base URL host is required");
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            throw new ModelConfigurationException("Base URL is not valid: " + baseUrl, ex);
        }
    }
}


