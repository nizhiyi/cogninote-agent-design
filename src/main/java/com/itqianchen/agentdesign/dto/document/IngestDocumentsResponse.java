package com.itqianchen.agentdesign.dto.document;

import java.util.List;

public record IngestDocumentsResponse(
        int scannedCount,
        int parsedCount,
        int skippedCount,
        int failedCount,
        List<IngestFailureResponse> failures
) {
}


