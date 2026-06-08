package com.itqianchen.agentdesign.dto.knowledge;

import jakarta.validation.constraints.NotBlank;

/**
 * Knowledge Folder Import 请求 定义 知识库 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record KnowledgeFolderImportRequest(
        @NotBlank String folderPath,
        Boolean recursive
) {
    /**
     * 读取 recursive Or Default 的最终值。
     * <p>当调用方没有显式配置时，返回当前模块约定的默认值。</p>
     */
    public boolean recursiveOrDefault() {
        return recursive == null || recursive;
    }
}
