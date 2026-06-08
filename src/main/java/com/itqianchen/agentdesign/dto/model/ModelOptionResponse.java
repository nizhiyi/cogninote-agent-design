package com.itqianchen.agentdesign.dto.model;

import com.itqianchen.agentdesign.domain.model.ModelCapability;

/**
 * Model Option 响应 定义返回给前端的 模型配置 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record ModelOptionResponse(
        String id,
        String name,
        ModelCapability capability
) {
}
