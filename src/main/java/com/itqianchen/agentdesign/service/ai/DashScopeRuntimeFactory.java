package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import org.springframework.stereotype.Component;

/**
 * Dash Scope 运行时 工厂 负责创建 AI 运行时 运行对象。
 * <p>提供商差异、客户端参数和缓存复用应收敛在这里。</p>
 */
@Component
public class DashScopeRuntimeFactory {

    private final DashScopeModelFactory dashScopeModelFactory;

    /**
     * 注入 DashScopeRuntimeFactory 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public DashScopeRuntimeFactory(DashScopeModelFactory dashScopeModelFactory) {
        this.dashScopeModelFactory = dashScopeModelFactory;
    }

    /**
     * 执行 AI 运行时 中的 chat 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public AiChatRuntime chatRuntime(ModelConfig config) {
        return new SpringAiChatRuntime("DashScope", dashScopeModelFactory.chatModel(config));
    }

    /**
     * 执行 AI 运行时 中的 embedding 运行时 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        return new DashScopeEmbeddingRuntime(dashScopeModelFactory, config);
    }
}
