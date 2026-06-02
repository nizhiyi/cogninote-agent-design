package com.itqianchen.agentdesign.domain.search;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.util.List;

public record IndexedDocument(
        String id,
        String sourcePath,
        String fileName,
        FileType fileType,
        List<IndexedChunk> chunks
) {
}


