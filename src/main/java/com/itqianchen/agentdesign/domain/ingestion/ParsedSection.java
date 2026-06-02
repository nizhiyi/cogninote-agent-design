package com.itqianchen.agentdesign.domain.ingestion;

public record ParsedSection(
        String content,
        String heading,
        Integer pageNumber
) {
}


