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
     * 知识库导入默认递归扫描，保证旧前端请求不传 recursive 时仍能发现子目录文档。
     */
    public boolean recursiveOrDefault() {
        return recursive == null || recursive;
    }
}
