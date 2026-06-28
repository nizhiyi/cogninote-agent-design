package com.itqianchen.agentdesign.service.websearch;

/**
 * Provider 归一化后的网页搜索结果。
 *
 * <p>该对象会返回给模型并转换为前端来源，只保留回答引用需要的公开网页信息。</p>
 */
public record WebSearchResultItem(
        String title,
        String url,
        String snippet,
        String provider,
        double score,
        String publishedAt
) {
}
