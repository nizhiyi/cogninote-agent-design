package com.itqianchen.agentdesign.service.graph;

import java.util.List;

/**
 * 模型返回的单 chunk 抽取 JSON 契约。
 *
 * <p>边关系采用 v2 契约：{@code type} 是后端内部粗分类，{@code displayLabel} 是模型直接输出的中文短谓词。
 * 前端展示语义不能再从英文 {@code type} 翻译得到。</p>
 */
public record GraphExtractionPayload(
        List<Node> nodes,
        List<Edge> edges
) {
    public record Node(
            String name,
            String type,
            String description,
            Double confidence,
            String quote
    ) {
    }

    public record Edge(
            String source,
            String target,
            String type,
            String displayLabel,
            String description,
            Double confidence,
            String quote
    ) {
    }
}
