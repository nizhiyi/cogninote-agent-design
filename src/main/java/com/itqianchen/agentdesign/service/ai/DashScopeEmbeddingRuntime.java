package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Dash Scope Embedding 运行时 封装外部 AI 运行时 调用。
 * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
 */
final class DashScopeEmbeddingRuntime implements AiEmbeddingRuntime {

    private static final String DOCUMENT_TEXT_TYPE = "document";
    private static final String QUERY_TEXT_TYPE = "query";

    private final DashScopeModelFactory dashScopeModelFactory;
    private final ModelConfig config;

    /**
     * 注入 DashScopeEmbeddingRuntime 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    DashScopeEmbeddingRuntime(DashScopeModelFactory dashScopeModelFactory, ModelConfig config) {
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.config = config;
    }

    /**
     * 执行 AI 运行时 中的 embed Query 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public float[] embedQuery(String query) {
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        return embeddingModel(QUERY_TEXT_TYPE).embed(query);
    }

    /**
     * 执行 AI 运行时 中的 embed Documents 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        return embeddingModel(DOCUMENT_TEXT_TYPE).embed(texts);
    }

    /**
     * 执行 AI 运行时 中的 embedding Model 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private EmbeddingModel embeddingModel(String textType) {
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        return dashScopeModelFactory.embeddingModel(config, textType);
    }
}
