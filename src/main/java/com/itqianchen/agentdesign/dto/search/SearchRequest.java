package com.itqianchen.agentdesign.dto.search;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import jakarta.validation.constraints.NotBlank;

/**
 * Search 请求 定义 检索索引 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record SearchRequest(
        @NotBlank String query,
        SearchMode mode,
        Integer topK
) {
    /**
     * 返回请求指定的检索模式，缺省时使用默认模式。
     * <p>默认值集中在 DTO 内，避免调用方重复兜底。</p>
     */
    public SearchMode modeOrDefault() {
        return mode == null ? SearchMode.HYBRID : mode;
    }
}


