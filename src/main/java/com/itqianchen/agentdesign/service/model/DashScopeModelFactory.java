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
 * 构造并缓存 DashScope 的 ChatModel 和 EmbeddingModel。
 *
 * <p>DashScope 的 endpoint 和 embedding textType 由模型名或调用场景决定，
 * 缓存 key 必须覆盖这些维度。</p>
 */
@Component
public class DashScopeModelFactory {

    private final ObservationRegistry observationRegistry;
    private volatile CachedChatModel cachedChatModel;
    private final ConcurrentMap<EmbeddingModelKey, EmbeddingModel> cachedEmbeddingModels = new ConcurrentHashMap<>();

    /**
     * 注入观测注册器。
     *
     * @param observationRegistry Micrometer 观测注册器；缺省时使用 NOOP
     */
    public DashScopeModelFactory(ObjectProvider<ObservationRegistry> observationRegistry) {
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
    }

    /**
     * 获取或创建 DashScope ChatModel。
     *
     * @param config Chat 模型配置
     * @return ChatModel
     */
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

    /**
     * 获取默认 textType 的 DashScope EmbeddingModel。
     *
     * @param config Embedding 配置
     * @return EmbeddingModel
     */
    public EmbeddingModel embeddingModel(ModelConfig config) {
        return embeddingModel(config, null);
    }

    /**
     * 获取指定 textType 的 DashScope EmbeddingModel。
     *
     * @param config Embedding 配置
     * @param textType DashScope query/document textType
     * @return EmbeddingModel
     */
    public EmbeddingModel embeddingModel(ModelConfig config, String textType) {
        EmbeddingModelKey key = EmbeddingModelKey.from(config, textType);
        // DashScope document/query 会使用不同 textType，必须按完整 key 缓存，避免两种实例互相覆盖。
        return cachedEmbeddingModels.computeIfAbsent(key, ignored -> buildEmbeddingModel(config, textType));
    }

    /**
     * 构造 DashScope ChatModel。
     *
     * @param config Chat 模型配置
     * @return ChatModel
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
     * 构造 DashScope EmbeddingModel。
     *
     * @param config Embedding 配置
     * @param textType DashScope query/document textType
     * @return EmbeddingModel
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
     * 构造 DashScopeApi 客户端。
     *
     * @param config 模型配置
     * @return DashScopeApi
     */
    private DashScopeApi api(ModelConfig config) {
        return DashScopeApi.builder()
                .baseUrl(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl(config.baseUrl()))
                .apiKey(config.apiKey())
                .build();
    }

    /**
     * DashScope Chat API endpoint 选择结果。
     *
     * <p>Spring AI Alibaba 依赖 multiModel 切换 text-generation 与 multimodal-generation，
     * 模型名前缀规则集中在这里维护。</p>
     */
    enum DashScopeChatEndpoint {
        /** 文本生成 endpoint。 */
        TEXT_GENERATION(false),

        /** 多模态生成 endpoint。 */
        MULTIMODAL_GENERATION(true);

        private final boolean multiModel;

        /**
         * 绑定 Spring AI Alibaba 的 multiModel 开关。
         *
         * @param multiModel 是否使用多模态 endpoint
         */
        DashScopeChatEndpoint(boolean multiModel) {
            this.multiModel = multiModel;
        }

        /**
         * 返回 Spring AI Alibaba endpoint 选择开关。
         *
         * @return 使用多模态 endpoint 时为 true
         */
        boolean multiModel() {
            return multiModel;
        }

        /**
         * 根据模型名前缀选择 DashScope endpoint。
         *
         * @param model 模型名
         * @return endpoint 选择结果
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
     * ChatModel 缓存 key，endpoint 也会影响最终请求路径，必须纳入比较。
     */
    private record ChatModelKey(
            String nativeBaseUrl,
            String apiKey,
            String chatModel,
            double temperature,
            DashScopeChatEndpoint endpoint
    ) {
        /**
         * 从模型配置提取 ChatModel 缓存维度。
         *
         * @param config Chat 模型配置
         * @return 可比较的缓存 key
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
     * EmbeddingModel 缓存 key，document/query textType 不同会改变 DashScope 服务端处理策略。
     */
    private record EmbeddingModelKey(
            String nativeBaseUrl,
            String apiKey,
            String embeddingModel,
            int embeddingDimensions,
            String textType
    ) {
        /**
         * 从模型配置和 textType 提取 EmbeddingModel 缓存维度。
         *
         * @param config Embedding 模型配置
         * @param textType DashScope query/document textType
         * @return 可比较的缓存 key
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
     * 与 key 绑定的 ChatModel 缓存项。
     */
    private record CachedChatModel(ChatModelKey key, ChatModel model) {
    }

}


