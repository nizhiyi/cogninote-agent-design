package com.itqianchen.agentdesign.domain.vo.search;

import com.itqianchen.agentdesign.domain.enums.document.FileType;
import java.util.List;

/**
 * 写入 Lucene 时使用的文档聚合。
 *
 * <p>Repository 会把 document 与 chunk 的扁平查询结果恢复成该结构，索引层不再回查数据库。</p>
 */
public record IndexedDocument(
        String id,
        String sourcePath,
        String fileName,
        FileType fileType,
        List<IndexedChunk> chunks
) {
}


