package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.chat.LlmGateway;
import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ModelRoutingLlmGateway implements LlmGateway {

    private final DashScopeModelFactory dashScopeModelFactory;
    private final OpenAiCompatibleClient openAiCompatibleClient;
    private final ChatPromptProperties promptProperties;

    public ModelRoutingLlmGateway(
            DashScopeModelFactory dashScopeModelFactory,
            OpenAiCompatibleClient openAiCompatibleClient,
            ChatPromptProperties promptProperties
    ) {
        this.dashScopeModelFactory = dashScopeModelFactory;
        this.openAiCompatibleClient = openAiCompatibleClient;
        this.promptProperties = promptProperties;
    }

    @Override
    public Flux<String> stream(ModelConfig config, Prompt prompt) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            return openAiCompatibleClient.stream(config, prompt);
        }

        ChatModel chatModel = dashScopeModelFactory.chatModel(config);
        return chatModel.stream(prompt)
                .map(response -> response.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isBlank());
    }

    @Override
    public void testConnection(ModelConfig config) {
        if (config.provider() == ModelProvider.OPENAI_COMPATIBLE) {
            openAiCompatibleClient.testConnection(config);
            return;
        }

        try {
            dashScopeModelFactory.chatModel(config)
                    .call(new Prompt(new UserMessage(promptProperties.connectionTest().user())));
        } catch (RuntimeException ex) {
            throw new ModelConfigurationException("DashScope connection failed: " + ex.getMessage(), ex);
        }
    }
}
