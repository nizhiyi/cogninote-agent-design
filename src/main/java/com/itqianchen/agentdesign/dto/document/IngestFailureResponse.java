package com.itqianchen.agentdesign.dto.document;

public record IngestFailureResponse(
        String sourcePath,
        String message
) {
}


