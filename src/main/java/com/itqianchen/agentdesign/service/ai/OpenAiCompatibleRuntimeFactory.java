package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.OpenAiCompatibleUrls;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class OpenAiCompatibleRuntimeFactory {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ObservationRegistry observationRegistry;
    private volatile CachedChatRuntime cachedChatRuntime;
    private volatile CachedEmbeddingRuntime cachedEmbeddingRuntime;

    public OpenAiCompatibleRuntimeFactory(
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectProvider<ObservationRegistry> observationRegistry
    ) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    public AiChatRuntime chatRuntime(ModelConfig config) {
        ChatRuntimeKey key = ChatRuntimeKey.from(config);
        CachedChatRuntime cached = cachedChatRuntime;
        if (cached != null && cached.key().equals(key)) {
            return cached.runtime();
        }

        synchronized (this) {
            cached = cachedChatRuntime;
            if (cached != null && cached.key().equals(key)) {
                return cached.runtime();
            }
            /*
             * OpenAI-compatible 的 Base URL 是用户运行时配置，不能交给 Spring Boot 自动配置。
             * 这里显式构造 OpenAiApi，并把 path 固定为 Base URL + /chat/completions。
             */
            AiChatRuntime runtime = new SpringAiChatRuntime("OpenAI-compatible", buildChatModel(config));
            cachedChatRuntime = new CachedChatRuntime(key, runtime);
            return runtime;
        }
    }

    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        EmbeddingRuntimeKey key = EmbeddingRuntimeKey.from(config);
        CachedEmbeddingRuntime cached = cachedEmbeddingRuntime;
        if (cached != null && cached.key().equals(key)) {
            return cached.runtime();
        }

        synchronized (this) {
            cached = cachedEmbeddingRuntime;
            if (cached != null && cached.key().equals(key)) {
                return cached.runtime();
            }
            AiEmbeddingRuntime runtime = new SpringAiEmbeddingRuntime(buildEmbeddingModel(config));
            cachedEmbeddingRuntime = new CachedEmbeddingRuntime(key, runtime);
            return runtime;
        }
    }

    private OpenAiChatModel buildChatModel(ModelConfig config) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.modelName())
                .temperature(config.resolvedTemperature())
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi(config))
                .defaultOptions(options)
                .toolCallingManager(ToolCallingManager.builder()
                        .observationRegistry(observationRegistry)
                        .build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(observationRegistry)
                .build();
    }

    private OpenAiEmbeddingModel buildEmbeddingModel(ModelConfig config) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.modelName())
                .dimensions(config.resolvedEmbeddingDimensions())
                .build();

        return new OpenAiEmbeddingModel(
                openAiApi(config),
                MetadataMode.EMBED,
                options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE,
                observationRegistry
        );
    }

    private OpenAiApi openAiApi(ModelConfig config) {
        return OpenAiApi.builder()
                .baseUrl(OpenAiCompatibleUrls.normalizeBaseUrl(config.baseUrl()))
                .apiKey(config.apiKey())
                .completionsPath(CHAT_COMPLETIONS_PATH)
                .embeddingsPath(EMBEDDINGS_PATH)
                .restClientBuilder(restClientBuilder.clone())
                .webClientBuilder(webClientBuilder.clone())
                .responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
                .build();
    }

    private record ChatRuntimeKey(
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature
    ) {
        private static ChatRuntimeKey from(ModelConfig config) {
            return new ChatRuntimeKey(
                    OpenAiCompatibleUrls.normalizeBaseUrl(config.baseUrl()),
                    config.apiKey(),
                    config.modelName(),
                    config.resolvedTemperature()
            );
        }
    }

    private record EmbeddingRuntimeKey(
            String baseUrl,
            String apiKey,
            String modelName,
            int embeddingDimensions
    ) {
        private static EmbeddingRuntimeKey from(ModelConfig config) {
            return new EmbeddingRuntimeKey(
                    OpenAiCompatibleUrls.normalizeBaseUrl(config.baseUrl()),
                    config.apiKey(),
                    config.modelName(),
                    config.resolvedEmbeddingDimensions()
            );
        }
    }

    private record CachedChatRuntime(ChatRuntimeKey key, AiChatRuntime runtime) {
        private CachedChatRuntime {
            Objects.requireNonNull(key);
            Objects.requireNonNull(runtime);
        }
    }

    private record CachedEmbeddingRuntime(EmbeddingRuntimeKey key, AiEmbeddingRuntime runtime) {
        private CachedEmbeddingRuntime {
            Objects.requireNonNull(key);
            Objects.requireNonNull(runtime);
        }
    }
}
