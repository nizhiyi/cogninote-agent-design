package com.itqianchen.agentdesign.document;

import jakarta.validation.constraints.NotBlank;

public record IngestDocumentsRequest(
        @NotBlank String folderPath,
        Boolean recursive
) {
    public boolean recursiveOrDefault() {
        return recursive == null || recursive;
    }
}
