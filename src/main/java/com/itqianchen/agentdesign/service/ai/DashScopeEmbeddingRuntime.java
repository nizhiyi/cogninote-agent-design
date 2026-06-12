package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * DashScope Embedding 运行时。
 *
 * <p>DashScope 检索模型要求 query 和 document 使用不同 textType，否则相似度效果会下降。</p>
 */
final class DashScopeEmbeddingRuntime implements AiEmbeddingRuntime {

    private static final String DOCUMENT_TEXT_TYPE = "document";
    private static final String QUERY_TEXT_TYPE = "query";

    private final DashScopeModelFactory dashScopeModelFactory;
    private final ModelConfig config;

    /**
     * 保留 DashScope 模型工厂和当前配置。
     *
     * <p>EmbeddingModel 按 textType 延迟获取，避免 query/document 共用错误的 DashScope 参数。</p>
     *
     * @param dashScopeModelFactory DashScope 模型工厂
     * @param config 当前 Embedding 配置
     */
    DashScopeEmbeddingRuntime(DashScopeModelFactory dashScopeModelFactory, ModelConfig config) {
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.config = config;
    }

    /**
     * 使用 DashScope query textType 生成检索向量。
     *
     * @param query 用户检索词
     * @return query 向量
     */
    @Override
    public float[] embedQuery(String query) {
        return embeddingModel(QUERY_TEXT_TYPE).embed(query);
    }

    /**
     * 使用 DashScope document textType 生成索引向量。
     *
     * @param texts 待索引文本
     * @return 文档向量列表
     */
    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embeddingModel(DOCUMENT_TEXT_TYPE).embed(texts);
    }

    /**
     * 按 DashScope 检索语义选择 EmbeddingModel。
     *
     * @param textType DashScope 要求的 query 或 document
     * @return 指定 textType 的模型实例
     */
    private EmbeddingModel embeddingModel(String textType) {
        return dashScopeModelFactory.embeddingModel(config, textType);
    }
}
