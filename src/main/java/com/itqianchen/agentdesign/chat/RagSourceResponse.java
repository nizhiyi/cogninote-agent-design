package com.itqianchen.agentdesign.chat;

import com.itqianchen.agentdesign.search.SearchHitResponse;

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
