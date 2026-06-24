package com.itqianchen.agentdesign.domain.dto.chat;

import com.itqianchen.agentdesign.domain.dto.search.SearchHitResponse;

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
     * 构造对话来源卡片的初始内容。
     *
     * <p>content 初始等于 preview；需要展开完整片段时再用 withContent 覆盖。</p>
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
     * 返回替换完整内容后的来源响应副本。
     *
     * <p>列表和 meta 事件只暴露 preview；用户展开来源时才填充 content，控制 SSE 和接口负载大小。</p>
     *
     * @param content 完整 chunk 内容；为空时表示隐藏完整内容
     * @return 替换 content 后的响应副本
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


