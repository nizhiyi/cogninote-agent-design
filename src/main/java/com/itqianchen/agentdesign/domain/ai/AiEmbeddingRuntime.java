package com.itqianchen.agentdesign.domain.ai;

import java.util.List;

/**
 * Ai Embedding 运行时 封装外部 AI 运行时 调用。
 * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
 */
public interface AiEmbeddingRuntime {

    /**
     * 执行 AI 运行时 中的 embed Query 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    float[] embedQuery(String query);

    /**
     * 执行 AI 运行时 中的 embed Documents 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    List<float[]> embedDocuments(List<String> texts);
}
