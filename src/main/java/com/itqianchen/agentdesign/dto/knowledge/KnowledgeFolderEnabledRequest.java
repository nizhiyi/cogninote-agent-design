package com.itqianchen.agentdesign.dto.knowledge;

import jakarta.validation.constraints.NotNull;

/**
 * Knowledge Folder Enabled 请求 定义 知识库 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record KnowledgeFolderEnabledRequest(@NotNull Boolean enabled) {
}
