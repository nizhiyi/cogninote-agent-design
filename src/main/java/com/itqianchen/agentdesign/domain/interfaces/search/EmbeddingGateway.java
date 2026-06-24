package com.itqianchen.agentdesign.domain.interfaces.search;

import java.util.List;

/**
 * 检索索引使用的 Embedding 能力网关。
 *
 * <p>领域层只依赖向量维度和向量生成契约，具体模型 Provider、SDK、鉴权和错误包装由实现层处理。</p>
 */
public interface EmbeddingGateway {

    /**
     * 当前是否可以执行向量生成。
     *
     * <p>返回 false 表示调用方向量搜索相关能力应提前短路，避免在请求中途触发外部模型失败。</p>
     */
    boolean isAvailable();

    /**
     * 返回当前 Embedding 模型的向量维度。
     *
     * <p>维度必须和写入 Lucene 的向量字段一致，否则新旧索引不能混用。</p>
     */
    int dimensions();

    /**
     * 为待索引文档 chunk 批量生成向量。
     *
     * @param texts 与索引 chunk 顺序一致的文本列表
     * @return 与输入顺序一一对应的向量列表
     */
    List<float[]> embedDocuments(List<String> texts);

    /**
     * 为用户检索 query 生成向量。
     *
     * @param query 用户检索词，调用方应先完成空白校验
     * @return 可直接传给 Lucene KNN 查询的向量
     */
    float[] embedQuery(String query);
}


