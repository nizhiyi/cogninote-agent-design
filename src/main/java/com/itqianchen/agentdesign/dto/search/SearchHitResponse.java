package com.itqianchen.agentdesign.dto.search;

public record SearchHitResponse(
        String chunkId,
        String documentId,
        String fileName,
        String sourcePath,
        String heading,
        Integer pageNumber,
        String preview,
        double score,
        Double keywordScore,
        Double vectorScore
) {
}


