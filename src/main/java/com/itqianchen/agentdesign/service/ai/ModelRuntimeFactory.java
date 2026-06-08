package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import org.springframework.stereotype.Component;

/**
 * Model 运行时 工厂 负责创建 模型配置 运行对象。
 * <p>提供商差异、客户端参数和缓存复用应收敛在这里。</p>
 */
@Component
public class ModelRuntimeFactory implements AiRuntimeFactory {

    private final DashScopeRuntimeFactory dashScopeRuntimeFactory;
    private final OpenAiCompatibleRuntimeFactory openAiCompatibleRuntimeFactory;

    /**
     * 注入 ModelRuntimeFactory 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ModelRuntimeFactory(
            DashScopeRuntimeFactory dashScopeRuntimeFactory,
            OpenAiCompatibleRuntimeFactory openAiCompatibleRuntimeFactory
    ) {
        this.dashScopeRuntimeFactory = dashScopeRuntimeFactory;
        this.openAiCompatibleRuntimeFactory = openAiCompatibleRuntimeFactory;
    }

    /**
     * 执行 模型配置 中的 chat 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public AiChatRuntime chatRuntime(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            // 这里开始真正的模型对话调用，后续 Flux 事件会驱动前端流式展示。
            return openAiCompatibleRuntimeFactory.chatRuntime(config);
        }
        // 这里开始真正的模型对话调用，后续 Flux 事件会驱动前端流式展示。
        return dashScopeRuntimeFactory.chatRuntime(config);
    }

    /**
     * 执行 模型配置 中的 embedding 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleRuntimeFactory.embeddingRuntime(config);
        }
        return dashScopeRuntimeFactory.embeddingRuntime(config);
    }
}
