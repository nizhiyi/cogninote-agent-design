package com.itqianchen.agentdesign.domain.ingestion;

/**
 * 解析器保留下来的原文区块。
 *
 * <p>heading 和 pageNumber 都是可选来源定位信息，调用方不能假设所有文件类型都能提供页码。</p>
 */
public record ParsedSection(
        String content,
        String heading,
        Integer pageNumber
) {
}


