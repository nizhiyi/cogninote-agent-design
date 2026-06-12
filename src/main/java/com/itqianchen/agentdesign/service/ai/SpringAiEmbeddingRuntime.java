package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 基于 Spring AI EmbeddingModel 的通用 Embedding 运行时。
 *
 * <p>适用于 query/document 不需要额外区分参数的 provider。</p>
 */
final class SpringAiEmbeddingRuntime implements AiEmbeddingRuntime {

    private final EmbeddingModel embeddingModel;

    /**
     * 绑定已经按用户配置构造的 EmbeddingModel。
     *
     * @param embeddingModel Spring AI EmbeddingModel
     */
    SpringAiEmbeddingRuntime(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 为检索 query 生成向量。
     *
     * <p>通用 Provider 不区分 query/document 参数，因此直接复用同一模型实例。</p>
     *
     * @param query 用户检索词
     * @return query 向量
     */
    @Override
    public float[] embedQuery(String query) {
        return embeddingModel.embed(query);
    }

    /**
     * 为文档 chunk 批量生成向量。
     *
     * @param texts 待索引文本，返回顺序与输入顺序一致
     * @return 文档向量列表
     */
    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embeddingModel.embed(texts);
    }
}
