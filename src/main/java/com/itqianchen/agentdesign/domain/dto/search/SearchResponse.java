package com.itqianchen.agentdesign.domain.dto.search;

import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import java.util.List;

/**
 * Search 响应 定义返回给前端的 检索索引 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record SearchResponse(
        String query,
        SearchMode mode,
        List<SearchHitResponse> hits
) {
}


