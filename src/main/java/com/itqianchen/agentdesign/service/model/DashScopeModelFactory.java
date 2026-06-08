package com.itqianchen.agentdesign.service.model;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import io.micrometer.observation.ObservationRegistry;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Dash Scope Model 工厂 负责创建 模型配置 运行对象。
 * <p>提供商差异、客户端参数和缓存复用应收敛在这里。</p>
 */
@Component
public class DashScopeModelFactory {

    private final ObservationRegistry observationRegistry;
    private volatile CachedChatModel cachedChatModel;
    private final ConcurrentMap<EmbeddingModelKey, EmbeddingModel> cachedEmbeddingModels = new ConcurrentHashMap<>();

    /**
     * 注入 DashScopeModelFactory 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public DashScopeModelFactory(ObjectProvider<ObservationRegistry> observationRegistry) {
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    /**
     * 执行 模型配置 中的 chat Model 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ChatModel chatModel(ModelConfig config) {
        ChatModelKey key = ChatModelKey.from(config);
        CachedChatModel cached = cachedChatModel;
        if (cached != null && cached.key().equals(key)) {
            return cached.model();
        }

        /**
         * 执行 模型配置 中的 synchronized 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
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

    /**
     * 执行 模型配置 中的 embedding Model 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public EmbeddingModel embeddingModel(ModelConfig config) {
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        return embeddingModel(config, null);
    }

    /**
     * 执行 模型配置 中的 embedding Model 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public EmbeddingModel embeddingModel(ModelConfig config, String textType) {
        EmbeddingModelKey key = EmbeddingModelKey.from(config, textType);
        // DashScope document/query 会使用不同 textType，必须按完整 key 缓存，避免两种实例互相覆盖。
        return cachedEmbeddingModels.computeIfAbsent(key, ignored -> buildEmbeddingModel(config, textType));
    }

    /**
     * 构建 build Chat Model 对象。
     * <p>第三方 API、框架对象或复杂参数的创建细节集中在此处。</p>
     */
    private ChatModel buildChatModel(ModelConfig config) {
        DashScopeChatEndpoint endpoint = DashScopeChatEndpoint.fromModel(config.modelName());
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(config.modelName())
                .temperature(config.resolvedTemperature())
                .stream(true)
                .incrementalOutput(true)
                .multiModel(endpoint.multiModel())
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

    /**
     * 构建 build Embedding Model 对象。
     * <p>第三方 API、框架对象或复杂参数的创建细节集中在此处。</p>
     */
    private EmbeddingModel buildEmbeddingModel(ModelConfig config, String textType) {
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(config.modelName())
                .dimensions(config.resolvedEmbeddingDimensions())
                .textType(textType)
                .build();

        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(api(config))
                .metadataMode(MetadataMode.EMBED)
                .defaultOptions(options)
                .retryTemplate(RetryTemplate.defaultInstance())
                .observationRegistry(observationRegistry)
                .build();
    }

    /**
     * 执行 模型配置 中的 api 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private DashScopeApi api(ModelConfig config) {
        return DashScopeApi.builder()
                .baseUrl(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl(config.baseUrl()))
                .apiKey(config.apiKey())
                .build();
    }

    /**
     * Dash Scope Chat Endpoint 枚举 模型配置 的稳定取值。
     * <p>枚举值可能进入数据库或 API 响应，修改时需要考虑兼容性。</p>
     */
    enum DashScopeChatEndpoint {
        TEXT_GENERATION(false),
        /**
         * 执行 模型配置 中的 MULTIMODAL GENERATION 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        MULTIMODAL_GENERATION(true);

        private final boolean multiModel;

        /**
         * 执行 模型配置 中的 Dash Scope Chat Endpoint 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        DashScopeChatEndpoint(boolean multiModel) {
            this.multiModel = multiModel;
        }

        /**
         * 执行 模型配置 中的 multi Model 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        boolean multiModel() {
            return multiModel;
        }

        /**
         * 执行 模型配置 中的 from Model 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        static DashScopeChatEndpoint fromModel(String model) {
            String normalized = model == null ? "" : model.trim().toLowerCase(Locale.ROOT);
            /*
             * 阿里百炼的 /models 会同时返回文本、视觉、语音和新一代多模态模型。
             * Spring AI Alibaba 只靠 multiModel 决定最终 endpoint：
             * false -> /text-generation/generation，true -> /multimodal-generation/generation。
             * qwen3.6+/qwen3.7+ 这类模型即使只传文本，也必须走多模态 endpoint；
             * 否则百炼会返回 “url error, please check url”。
             */
            if (normalized.startsWith("qwen3.5-")
                    || normalized.startsWith("qwen3.6-")
                    || normalized.startsWith("qwen3.7-")
                    || normalized.contains("-vl-")
                    || normalized.contains("-omni-")
                    || normalized.contains("-image-")
                    || normalized.contains("-asr-")
                    || normalized.contains("-tts-")
                    || normalized.contains("-livetranslate-")) {
                return MULTIMODAL_GENERATION;
            }
            return TEXT_GENERATION;
        }
    }

    /**
     * Chat Model Key 是 模型配置 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record ChatModelKey(
            String nativeBaseUrl,
            String apiKey,
            String chatModel,
            double temperature,
            DashScopeChatEndpoint endpoint
    ) {
        /**
         * 将领域对象转换为 DashScopeModelFactory。
         * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
         */
        private static ChatModelKey from(ModelConfig config) {
            return new ChatModelKey(
                    DashScopeBaseUrls.toSpringAiAlibabaBaseUrl(config.baseUrl()),
                    config.apiKey(),
                    config.modelName(),
                    config.resolvedTemperature(),
                    DashScopeChatEndpoint.fromModel(config.modelName())
            );
        }
    }

    /**
     * Embedding Model Key 是 模型配置 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record EmbeddingModelKey(
            String nativeBaseUrl,
            String apiKey,
            String embeddingModel,
            int embeddingDimensions,
            String textType
    ) {
        /**
         * 将领域对象转换为 DashScopeModelFactory。
         * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
         */
        private static EmbeddingModelKey from(ModelConfig config, String textType) {
            return new EmbeddingModelKey(
                    DashScopeBaseUrls.toSpringAiAlibabaBaseUrl(config.baseUrl()),
                    config.apiKey(),
                    config.modelName(),
                    config.resolvedEmbeddingDimensions(),
                    textType
            );
        }
    }

    /**
     * Cached Chat Model 是 模型配置 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record CachedChatModel(ChatModelKey key, ChatModel model) {
    }

}


