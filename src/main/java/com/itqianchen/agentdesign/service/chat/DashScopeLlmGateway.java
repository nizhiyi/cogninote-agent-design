package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.chat.LlmGateway;
import com.itqianchen.agentdesign.service.model.DashScopeModelFactory;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class DashScopeLlmGateway implements LlmGateway {

    private final DashScopeModelFactory modelFactory;

    public DashScopeLlmGateway(DashScopeModelFactory modelFactory) {
        this.modelFactory = modelFactory;
    }

    @Override
    public Flux<String> stream(ModelConfig config, Prompt prompt) {
        ChatModel chatModel = modelFactory.chatModel(config);
        return chatModel.stream(prompt)
                .map(response -> response.getResult().getOutput().getText())
                .filter(text -> text != null && !text.isBlank());
    }

    @Override
    public void testConnection(ModelConfig config) {
        try {
            modelFactory.chatModel(config)
                    .call(new Prompt(new UserMessage("请用一句话回答：CogniNote 连接测试是否可用？")));
        } catch (RuntimeException ex) {
            throw new ModelConfigurationException("DashScope connection failed: " + ex.getMessage(), ex);
        }
    }
}


