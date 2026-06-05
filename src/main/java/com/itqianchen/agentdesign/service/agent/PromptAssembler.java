package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class PromptAssembler {

    private final ChatPromptProperties promptProperties;

    public PromptAssembler(ChatPromptProperties promptProperties) {
        this.promptProperties = promptProperties;
    }

    public Prompt assembleRagPrompt(String question, String context) {
        String systemPrompt = promptProperties.rag().system();
        String userPrompt = promptProperties.rag().user()
                .replace("{question}", question)
                .replace("{context}", context);
        return new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
    }
}
