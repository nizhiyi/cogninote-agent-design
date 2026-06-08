package com.itqianchen.agentdesign.domain.ingestion;

/**
 * Parsed Section 是 文档解析 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record ParsedSection(
        String content,
        String heading,
        Integer pageNumber
) {
}


