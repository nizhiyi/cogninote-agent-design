package com.itqianchen.agentdesign.dto.search;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query,
        SearchMode mode,
        Integer topK
) {
    public SearchMode modeOrDefault() {
        return mode == null ? SearchMode.HYBRID : mode;
    }
}


