package com.itqianchen.agentdesign.domain.search;

/**
 * 已持久化 chunk 的来源详情。
 *
 * <p>聊天来源和图谱证据只保存 chunkId，打开详情时通过该结构补齐完整内容与文件路径。</p>
 */
public record StoredChunk(
        String chunkId,
        String documentId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading,
        String fileName,
        String sourcePath
) {
}


