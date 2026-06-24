package com.itqianchen.agentdesign.domain.dto.search;

/**
 * Search Hit 响应 定义返回给前端的 检索索引 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record SearchHitResponse(
        String chunkId,
        String documentId,
        String fileName,
        String sourcePath,
        String heading,
        Integer pageNumber,
        String preview,
        double score,
        Double keywordScore,
        Double vectorScore
) {
}


