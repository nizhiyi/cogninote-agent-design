package com.itqianchen.agentdesign.chat;

import com.itqianchen.agentdesign.search.SearchMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatStreamRequest(
        @NotBlank @Size(max = 4000) String question,
        Integer topK,
        SearchMode mode
) {
}
