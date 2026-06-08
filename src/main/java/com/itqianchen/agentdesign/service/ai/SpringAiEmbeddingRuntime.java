package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Spring Ai Embedding 运行时 封装外部 AI 运行时 调用。
 * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
 */
final class SpringAiEmbeddingRuntime implements AiEmbeddingRuntime {

    private final EmbeddingModel embeddingModel;

    /**
     * 注入 SpringAiEmbeddingRuntime 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    SpringAiEmbeddingRuntime(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 执行 AI 运行时 中的 embed Query 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public float[] embedQuery(String query) {
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        return embeddingModel.embed(query);
    }

    /**
     * 执行 AI 运行时 中的 embed Documents 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
        return embeddingModel.embed(texts);
    }
}
