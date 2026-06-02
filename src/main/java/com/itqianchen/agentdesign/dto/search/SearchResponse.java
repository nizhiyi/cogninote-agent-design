package com.itqianchen.agentdesign.dto.search;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import java.util.List;

public record SearchResponse(
        String query,
        SearchMode mode,
        List<SearchHitResponse> hits
) {
}


