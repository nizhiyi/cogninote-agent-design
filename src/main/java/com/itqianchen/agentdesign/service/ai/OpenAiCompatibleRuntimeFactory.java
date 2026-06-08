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

/**
 * Open Ai Compatible 运行时 工厂 负责创建 AI 运行时 运行对象。
 * <p>提供商差异、客户端参数和缓存复用应收敛在这里。</p>
 */
@Component
public class OpenAiCompatibleRuntimeFactory {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ObservationRegistry observationRegistry;
    private volatile CachedChatRuntime cachedChatRuntime;
    private volatile CachedEmbeddingRuntime cachedEmbeddingRuntime;

    /**
     * 注入 OpenAiCompatibleRuntimeFactory 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public OpenAiCompatibleRuntimeFactory(
            // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder,
            ObjectProvider<ObservationRegistry> observationRegistry
    ) {
        // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    /**
     * 执行 AI 运行时 中的 chat 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public AiChatRuntime chatRuntime(ModelConfig config) {
        ChatRuntimeKey key = ChatRuntimeKey.from(config);
        CachedChatRuntime cached = cachedChatRuntime;
        if (cached != null && cached.key().equals(key)) {
            return cached.runtime();
        }

        /**
         * 执行 AI 运行时 中的 synchronized 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
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

    /**
     * 执行 AI 运行时 中的 embedding 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        EmbeddingRuntimeKey key = EmbeddingRuntimeKey.from(config);
        CachedEmbeddingRuntime cached = cachedEmbeddingRuntime;
        if (cached != null && cached.key().equals(key)) {
            return cached.runtime();
        }

        /**
         * 执行 AI 运行时 中的 synchronized 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
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

    /**
     * 构建 build Chat Model 对象。
     * <p>第三方 API、框架对象或复杂参数的创建细节集中在此处。</p>
     */
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

    /**
     * 构建 build Embedding Model 对象。
     * <p>第三方 API、框架对象或复杂参数的创建细节集中在此处。</p>
     */
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

    /**
     * 执行 AI 运行时 中的 open Ai Api 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private OpenAiApi openAiApi(ModelConfig config) {
        return OpenAiApi.builder()
                .baseUrl(OpenAiCompatibleUrls.normalizeBaseUrl(config.baseUrl()))
                .apiKey(config.apiKey())
                .completionsPath(CHAT_COMPLETIONS_PATH)
                .embeddingsPath(EMBEDDINGS_PATH)
                // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
                .restClientBuilder(restClientBuilder.clone())
                .webClientBuilder(webClientBuilder.clone())
                .responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
                .build();
    }

    /**
     * Chat 运行时 Key 是 AI 运行时 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record ChatRuntimeKey(
            String baseUrl,
            String apiKey,
            String modelName,
            double temperature
    ) {
        /**
         * 将领域对象转换为 OpenAiCompatibleRuntimeFactory。
         * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
         */
        private static ChatRuntimeKey from(ModelConfig config) {
            return new ChatRuntimeKey(
                    OpenAiCompatibleUrls.normalizeBaseUrl(config.baseUrl()),
                    config.apiKey(),
                    config.modelName(),
                    config.resolvedTemperature()
            );
        }
    }

    /**
     * Embedding 运行时 Key 是 AI 运行时 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record EmbeddingRuntimeKey(
            String baseUrl,
            String apiKey,
            String modelName,
            int embeddingDimensions
    ) {
        /**
         * 将领域对象转换为 OpenAiCompatibleRuntimeFactory。
         * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
         */
        private static EmbeddingRuntimeKey from(ModelConfig config) {
            return new EmbeddingRuntimeKey(
                    OpenAiCompatibleUrls.normalizeBaseUrl(config.baseUrl()),
                    config.apiKey(),
                    config.modelName(),
                    config.resolvedEmbeddingDimensions()
            );
        }
    }

    /**
     * Cached Chat 运行时 封装外部 AI 运行时 调用。
     * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
     */
    private record CachedChatRuntime(ChatRuntimeKey key, AiChatRuntime runtime) {
        private CachedChatRuntime {
            Objects.requireNonNull(key);
            Objects.requireNonNull(runtime);
        }
    }

    /**
     * Cached Embedding 运行时 封装外部 AI 运行时 调用。
     * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
     */
    private record CachedEmbeddingRuntime(EmbeddingRuntimeKey key, AiEmbeddingRuntime runtime) {
        private CachedEmbeddingRuntime {
            Objects.requireNonNull(key);
            Objects.requireNonNull(runtime);
        }
    }
}
