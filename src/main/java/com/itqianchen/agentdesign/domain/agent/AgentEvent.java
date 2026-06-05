package com.itqianchen.agentdesign.domain.agent;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import java.util.List;

public sealed interface AgentEvent permits AgentEvent.Meta, AgentEvent.Delta, AgentEvent.Done, AgentEvent.Error {

    record Meta(String conversationId, SearchMode retrievalMode, List<RagSourceResponse> sources) implements AgentEvent {
    }

    record Delta(String text) implements AgentEvent {
    }

    record Done(Object usage) implements AgentEvent {
    }

    record Error(String message) implements AgentEvent {
    }
}
