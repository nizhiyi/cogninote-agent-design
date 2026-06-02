package com.itqianchen.agentdesign.chat;

import com.itqianchen.agentdesign.search.SearchMode;
import java.util.List;
import reactor.core.publisher.Flux;

public record RagChatStream(
        String conversationId,
        SearchMode retrievalMode,
        List<RagSourceResponse> sources,
        Flux<String> answer
) {
}
