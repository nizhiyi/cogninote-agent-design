package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import org.springframework.stereotype.Component;

/**
 * 按 ModelProvider 分派到具体厂商运行时工厂。
 *
 * <p>这是模型配置到运行时的唯一路由点，新增 provider 时应同步扩展该分派逻辑。</p>
 */
@Component
public class ModelRuntimeFactory implements AiRuntimeFactory {

    private final DashScopeRuntimeFactory dashScopeRuntimeFactory;
    private final OpenAiCompatibleRuntimeFactory openAiCompatibleRuntimeFactory;

    /**
     * 注入所有已支持 Provider 的运行时工厂。
     *
     * @param dashScopeRuntimeFactory DashScope 运行时工厂
     * @param openAiCompatibleRuntimeFactory OpenAI-compatible 运行时工厂
     */
    public ModelRuntimeFactory(
            DashScopeRuntimeFactory dashScopeRuntimeFactory,
            OpenAiCompatibleRuntimeFactory openAiCompatibleRuntimeFactory
    ) {
        this.dashScopeRuntimeFactory = dashScopeRuntimeFactory;
        this.openAiCompatibleRuntimeFactory = openAiCompatibleRuntimeFactory;
    }

    /**
     * 按 Provider 创建 Chat 运行时。
     *
     * @param config 已归一化的模型配置
     * @return 与 Provider 匹配的 Chat 运行时
     */
    @Override
    public AiChatRuntime chatRuntime(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleRuntimeFactory.chatRuntime(config);
        }
        return dashScopeRuntimeFactory.chatRuntime(config);
    }

    /**
     * 按 Provider 创建 Embedding 运行时。
     *
     * @param config 已归一化的模型配置
     * @return 与 Provider 匹配的 Embedding 运行时
     */
    @Override
    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleRuntimeFactory.embeddingRuntime(config);
        }
        return dashScopeRuntimeFactory.embeddingRuntime(config);
    }
}
