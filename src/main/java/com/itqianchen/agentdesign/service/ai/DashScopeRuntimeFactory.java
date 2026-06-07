package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import org.springframework.stereotype.Component;

@Component
public class DashScopeRuntimeFactory {

    private final DashScopeModelFactory dashScopeModelFactory;

    public DashScopeRuntimeFactory(DashScopeModelFactory dashScopeModelFactory) {
        this.dashScopeModelFactory = dashScopeModelFactory;
    }

    public AiChatRuntime chatRuntime(ModelConfig config) {
        return new SpringAiChatRuntime("DashScope", dashScopeModelFactory.chatModel(config));
    }

    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        return new DashScopeEmbeddingRuntime(dashScopeModelFactory, config);
    }
}
