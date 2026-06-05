package com.itqianchen.agentdesign.domain.ai;

import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public interface AiChatRuntime {

    Flux<String> stream(Prompt prompt);

    void testConnection(Prompt prompt);
}
