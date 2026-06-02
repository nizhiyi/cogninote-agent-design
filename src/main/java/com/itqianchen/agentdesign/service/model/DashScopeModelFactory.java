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
                .dashScopeApi(api(config.apiKey()))
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
                .dashScopeApi(api(config.apiKey()))
                .metadataMode(MetadataMode.EMBED)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.defaultInstance())
                .observationRegistry(observationRegistry)
                .build();
    }

    private DashScopeApi api(String apiKey) {
        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }

    private record ChatModelKey(String apiKey, String chatModel, double temperature) {
        private static ChatModelKey from(ModelConfig config) {
            return new ChatModelKey(config.apiKey(), config.chatModel(), config.temperature());
        }
    }

    private record EmbeddingModelKey(String apiKey, String embeddingModel, int embeddingDimensions) {
        private static EmbeddingModelKey from(ModelConfig config) {
            return new EmbeddingModelKey(config.apiKey(), config.embeddingModel(), config.embeddingDimensions());
        }
    }

    private record CachedChatModel(ChatModelKey key, ChatModel model) {
    }

    private record CachedEmbeddingModel(EmbeddingModelKey key, EmbeddingModel model) {
    }
}


