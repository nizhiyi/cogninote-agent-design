package com.itqianchen.agentdesign.dto.chat;

import com.itqianchen.agentdesign.dto.search.SearchHitResponse;

/**
 * Rag Source 响应 定义返回给前端的 聊天会话 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record RagSourceResponse(
        int index,
        String chunkId,
        String documentId,
        String fileName,
        String sourcePath,
        String heading,
        Integer pageNumber,
        String preview,
        String content,
        double score
) {
    /**
     * 将领域对象转换为 RagSourceResponse。
     * <p>字段映射集中在这里，减少控制器和服务层的重复拼装。</p>
     */
    public static RagSourceResponse from(int index, SearchHitResponse hit) {
        return new RagSourceResponse(
                index,
                hit.chunkId(),
                hit.documentId(),
                hit.fileName(),
                hit.sourcePath(),
                hit.heading(),
                hit.pageNumber(),
                hit.preview(),
                hit.preview(),
                hit.score()
        );
    }

    /**
     * 返回应用 with Content 后的新对象。
     * <p>不可变数据通过复制表达变更，避免调用方误改原对象。</p>
     */
    public RagSourceResponse withContent(String content) {
        return new RagSourceResponse(
                index,
                chunkId,
                documentId,
                fileName,
                sourcePath,
                heading,
                pageNumber,
                preview,
                content,
                score
        );
    }
}


