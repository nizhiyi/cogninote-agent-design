package com.itqianchen.agentdesign.ingestion;

public record ParsedSection(
        String content,
        String heading,
        Integer pageNumber
) {
}
