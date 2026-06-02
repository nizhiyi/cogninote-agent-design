package com.itqianchen.agentdesign.dto.chat;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import java.util.List;

public record ChatMetaEvent(
        String conversationId,
        SearchMode retrievalMode,
        List<RagSourceResponse> sources
) {
    public ChatMetaEvent(String conversationId, SearchMode retrievalMode, List<RagSourceResponse> sources) {
        this.conversationId = conversationId;
        this.retrievalMode = retrievalMode;
        this.sources = sources.stream()
                .map(source -> source.withContent(null))
                .toList();
    }
}


