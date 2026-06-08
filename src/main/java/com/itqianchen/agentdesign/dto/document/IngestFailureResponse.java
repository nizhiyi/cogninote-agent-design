package com.itqianchen.agentdesign.dto.document;

/**
 * Ingest Failure 响应 定义返回给前端的 文档管理 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record IngestFailureResponse(
        String sourcePath,
        String message
) {
}


