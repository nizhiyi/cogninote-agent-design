package com.itqianchen.agentdesign.service.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class DashScopeModelFactory {

    private static final String DASHSCOPE_COMPATIBLE_SUFFIX = "/compatible-mode/v1";
    private static final String DASHSCOPE_NATIVE_BASE_URL = "https://dashscope.aliyuncs.com";

    private final ObservationRegistry observationRegistry;
    private volatile CachedChatModel cachedChatModel;
    private volatile CachedEmbeddingModel cachedEmbeddingModel;

    public DashScopeModelFactory(ObjectProvider<ObservationRegistry> observationRegistry) {
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    public ChatModel chatModel(ModelConfig config) {
        ChatModelKey key = ChatModelKey.from(config);
        CachedChatModel cached = cachedChatModel;
        if (cached != null && cached.key().equals(key)) {
            return cached.model();
        }

        synchronized (this) {
            cached = cachedChatModel;
            if (cached != null && cached.key().equals(key)) {
                return cached.model();
            }
            // DashScope 模型创建会装配 HTTP 客户端和重试策略。
            // 按有效配置缓存，避免每次对话或索引都重建客户端。
            ChatModel model = buildChatModel(config);
            cachedChatModel = new CachedChatModel(key, model);
            return model;
        }
    }

    public EmbeddingModel embeddingModel(ModelConfig config) {
        EmbeddingModelKey key = EmbeddingModelKey.from(config);
        CachedEmbeddingModel cached = cachedEmbeddingModel;
        if (cached != null && cached.key().equals(key)) {
            return cached.model();
        }

        synchronized (this) {
            cached = cachedEmbeddingModel;
            if (cached != null && cached.key().equals(key)) {
                return cached.model();
            }
            // Embedding 在批量索引时调用频繁，同样需要按配置复用模型实例。
            EmbeddingModel model = buildEmbeddingModel(config);
            cachedEmbeddingModel = new CachedEmbeddingModel(key, model);
            return model;
        }
    }

    private ChatModel buildChatModel(ModelConfig config) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(config.chatModel())
                .temperature(config.temperature())
                .stream(true)
                .incrementalOutput(true)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(api(config))
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.defaultInstance())
                .toolCallingManager(ToolCallingManager.builder()
                        .observationRegistry(observationRegistry)
                        .build())
                .observationRegistry(observationRegistry)
                .build();
    }

    private EmbeddingModel buildEmbeddingModel(ModelConfig config) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(config.embeddingModel())
                .dimensions(config.embeddingDimensions())
                .build();

        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(api(config))
                .metadataMode(MetadataMode.EMBED)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.defaultInstance())
                .observationRegistry(observationRegistry)
                .build();
    }

    private DashScopeApi api(ModelConfig config) {
        return DashScopeApi.builder()
                .baseUrl(toDashScopeNativeBaseUrl(config.baseUrl()))
                .apiKey(config.apiKey())
                .build();
    }

    private static String toDashScopeNativeBaseUrl(String configuredBaseUrl) {
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            return DASHSCOPE_NATIVE_BASE_URL;
        }
        String normalized = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                : configuredBaseUrl;
        if (normalized.endsWith(DASHSCOPE_COMPATIBLE_SUFFIX)) {
            // 配置页使用 compatible Base URL 读取 /models；
            // Spring AI Alibaba 当前 DashScopeApi 仍发送原生 DashScope 请求体，
            // 因此实际 Chat/Embedding 调用要回到同一主机的原生 API 根路径。
            return normalized.substring(0, normalized.length() - DASHSCOPE_COMPATIBLE_SUFFIX.length());
        }
        return normalized;
    }

    private record ChatModelKey(String baseUrl, String apiKey, String chatModel, double temperature) {
        private static ChatModelKey from(ModelConfig config) {
            return new ChatModelKey(config.baseUrl(), config.apiKey(), config.chatModel(), config.temperature());
        }
    }

    private record EmbeddingModelKey(String baseUrl, String apiKey, String embeddingModel, int embeddingDimensions) {
        private static EmbeddingModelKey from(ModelConfig config) {
            return new EmbeddingModelKey(
                    config.baseUrl(),
                    config.apiKey(),
                    config.embeddingModel(),
                    config.embeddingDimensions()
            );
        }
    }

    private record CachedChatModel(ChatModelKey key, ChatModel model) {
    }

    private record CachedEmbeddingModel(EmbeddingModelKey key, EmbeddingModel model) {
    }
}


