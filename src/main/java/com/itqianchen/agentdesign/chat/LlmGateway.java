package com.itqianchen.agentdesign.chat;

import com.itqianchen.agentdesign.model.ModelConfig;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public interface LlmGateway {

    Flux<String> stream(ModelConfig config, Prompt prompt);

    void testConnection(ModelConfig config);
}
