package com.itqianchen.agentdesign.domain.search;

import java.util.List;

/**
 * Embedding 网关 将基础设施能力适配为 检索索引 领域网关。
 * <p>领域层通过网关调用外部能力，避免直接耦合具体 SDK。</p>
 */
public interface EmbeddingGateway {

    /**
     * 判断 is Available 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    boolean isAvailable();

    /**
     * 执行 检索索引 中的 dimensions 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    int dimensions();

    /**
     * 执行 检索索引 中的 embed Documents 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    List<float[]> embedDocuments(List<String> texts);

    /**
     * 执行 检索索引 中的 embed Query 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    float[] embedQuery(String query);
}


