package com.itqianchen.agentdesign.domain.search;

public record IndexStatistics(
        long parsedDocumentCount,
        long unindexedDocumentCount,
        Long lastIndexedAt
) {
}


