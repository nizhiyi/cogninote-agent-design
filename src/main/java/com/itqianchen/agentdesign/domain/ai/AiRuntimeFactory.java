package com.itqianchen.agentdesign.domain.ai;

import com.itqianchen.agentdesign.domain.model.ModelConfig;

/**
 * Ai 运行时 工厂 负责创建 AI 运行时 运行对象。
 * <p>提供商差异、客户端参数和缓存复用应收敛在这里。</p>
 */
public interface AiRuntimeFactory {

    /**
     * 执行 AI 运行时 中的 chat 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    AiChatRuntime chatRuntime(ModelConfig config);

    /**
     * 执行 AI 运行时 中的 embedding 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    AiEmbeddingRuntime embeddingRuntime(ModelConfig config);
}
