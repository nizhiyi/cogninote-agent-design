package com.itqianchen.agentdesign.domain.ai;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

public interface AiChatRuntime {

    Flux<String> stream(Prompt prompt);

    Flux<String> stream(String systemPrompt, String userMessage, List<Advisor> advisors, Map<String, Object> advisorParams);

    String callText(String systemPrompt, String userMessage);

    void testConnection(Prompt prompt);
}
