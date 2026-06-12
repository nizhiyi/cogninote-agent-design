package com.itqianchen.agentdesign.dto.document;

import jakarta.validation.constraints.NotBlank;

/**
 * Ingest Documents 请求 定义 文档管理 接口允许接收的请求字段。
 * <p>字段校验应和前端表单、接口文档保持一致。</p>
 */
public record IngestDocumentsRequest(
        @NotBlank String folderPath,
        Boolean recursive
) {
    /**
     * 导入目录默认递归扫描，沿用旧接口未传 recursive 时的行为。
     */
    public boolean recursiveOrDefault() {
        return recursive == null || recursive;
    }
}


