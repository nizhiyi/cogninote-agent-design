package com.itqianchen.agentdesign.mapper.document;

import com.itqianchen.agentdesign.domain.document.FileType;

/**
 * Indexed Document Row 表示 文档管理 查询返回的数据库行投影。
 * <p>字段需要和 Mapper SQL 别名保持一致。</p>
 */
public record IndexedDocumentRow(
        String documentId,
        String sourcePath,
        String fileName,
        FileType fileType,
        String chunkId,
        int chunkIndex,
        String content,
        String contentHash,
        Integer pageNumber,
        String heading
) {
}
