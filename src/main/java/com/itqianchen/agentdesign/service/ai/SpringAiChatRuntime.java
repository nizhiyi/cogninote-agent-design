package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import org.springframework.ai.chat.model.ChatResponse;
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
                .<String>handle((response, sink) -> {
                    String text = extractText(response);
                    if (text != null && !text.isEmpty()) {
                        sink.next(text);
                    }
                });
    }

    @Override
    public void testConnection(Prompt prompt) {
        try {
            chatModel.call(prompt);
        } catch (RuntimeException ex) {
            throw new ModelConfigurationException(providerLabel + " connection failed: " + ex.getMessage(), ex);
        }
    }

    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        // Spring AI/OpenAI-compatible 流可能发出只含元数据的结束片段。
        // 但空格和换行可能是独立 chunk，Markdown 语法依赖这些空白，不能用 isBlank 过滤。
        return response.getResult().getOutput().getText();
    }
}
