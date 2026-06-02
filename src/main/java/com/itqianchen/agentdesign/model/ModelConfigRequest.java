package com.itqianchen.agentdesign.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModelConfigRequest(
        @Size(max = 4096) String apiKey,
        @NotBlank @Size(max = 120) String chatModel,
        @NotBlank @Size(max = 120) String embeddingModel,
        @Min(1) @Max(8192) Integer embeddingDimensions,
        @Min(0) @Max(2) Double temperature,
        @Min(1) @Max(50) Integer topK
) {
}
