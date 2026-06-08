package com.itqianchen.agentdesign.domain.search;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.util.List;

/**
 * Indexed Document 是 文档管理 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record IndexedDocument(
        String id,
        String sourcePath,
        String fileName,
        FileType fileType,
        List<IndexedChunk> chunks
) {
}


