package com.itqianchen.agentdesign.document;

public record IngestFailureResponse(
        String sourcePath,
        String message
) {
}
