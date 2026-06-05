package com.itqianchen.agentdesign.domain.ai;

import com.itqianchen.agentdesign.domain.model.ModelConfig;

public interface AiRuntimeFactory {

    AiChatRuntime chatRuntime(ModelConfig config);

    AiEmbeddingRuntime embeddingRuntime(ModelConfig config);
}
