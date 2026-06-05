package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

final class SpringAiChatRuntime implements AiChatRuntime {

    private final String providerLabel;
    private final ChatModel chatModel;

    SpringAiChatRuntime(String providerLabel, ChatModel chatModel) {
        this.providerLabel = providerLabel;
        this.chatModel = chatModel;
    }

    @Override
    public Flux<String> stream(Prompt prompt) {
        return chatModel.stream(prompt)
                .map(response -> response.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isBlank());
    }

    @Override
    public void testConnection(Prompt prompt) {
        try {
            chatModel.call(prompt);
        } catch (RuntimeException ex) {
            throw new ModelConfigurationException(providerLabel + " connection failed: " + ex.getMessage(), ex);
        }
    }
}
