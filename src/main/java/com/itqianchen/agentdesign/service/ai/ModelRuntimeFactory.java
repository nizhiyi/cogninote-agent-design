package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import org.springframework.stereotype.Component;

@Component
public class ModelRuntimeFactory implements AiRuntimeFactory {

    private final DashScopeRuntimeFactory dashScopeRuntimeFactory;
    private final OpenAiCompatibleRuntimeFactory openAiCompatibleRuntimeFactory;

    public ModelRuntimeFactory(
            DashScopeRuntimeFactory dashScopeRuntimeFactory,
            OpenAiCompatibleRuntimeFactory openAiCompatibleRuntimeFactory
    ) {
        this.dashScopeRuntimeFactory = dashScopeRuntimeFactory;
        this.openAiCompatibleRuntimeFactory = openAiCompatibleRuntimeFactory;
    }

    @Override
    public AiChatRuntime chatRuntime(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleRuntimeFactory.chatRuntime(config);
        }
        return dashScopeRuntimeFactory.chatRuntime(config);
    }

    @Override
    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleRuntimeFactory.embeddingRuntime(config);
        }
        return dashScopeRuntimeFactory.embeddingRuntime(config);
    }
}
