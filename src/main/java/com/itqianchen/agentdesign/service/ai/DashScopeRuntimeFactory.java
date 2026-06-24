package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.interfaces.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.interfaces.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.entity.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import org.springframework.stereotype.Component;

/**
 * 把 DashScope Spring AI 模型包装成本地 AI 运行时接口。
 *
 * <p>Embedding 需要区分 document/query textType，因此由专门运行时延迟选择模型实例。</p>
 */
@Component
public class DashScopeRuntimeFactory {

    private final DashScopeModelFactory dashScopeModelFactory;

    /**
     * 注入 DashScope 模型工厂。
     *
     * @param dashScopeModelFactory 负责构造并缓存 DashScope Spring AI 模型
     */
    public DashScopeRuntimeFactory(DashScopeModelFactory dashScopeModelFactory) {
        this.dashScopeModelFactory = dashScopeModelFactory;
    }

    /**
     * 创建 DashScope Chat 运行时。
     *
     * @param config 已归一化的模型配置
     * @return Chat 运行时
     */
    public AiChatRuntime chatRuntime(ModelConfig config) {
        return new SpringAiChatRuntime("DashScope", dashScopeModelFactory.chatModel(config));
    }

    /**
     * 创建 DashScope Embedding 运行时。
     *
     * <p>返回专用运行时以区分 query/document textType。</p>
     *
     * @param config 已归一化的模型配置
     * @return Embedding 运行时
     */
    public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
        return new DashScopeEmbeddingRuntime(dashScopeModelFactory, config);
    }
}
