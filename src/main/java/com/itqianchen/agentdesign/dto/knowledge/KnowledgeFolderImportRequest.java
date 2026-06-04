package com.itqianchen.agentdesign.dto.knowledge;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeFolderImportRequest(
        @NotBlank String folderPath,
        Boolean recursive
) {
    public boolean recursiveOrDefault() {
        return recursive == null || recursive;
    }
}
