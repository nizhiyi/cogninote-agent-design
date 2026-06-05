package com.itqianchen.agentdesign.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class SpringAiChatRuntimeTests {

    @Test
    void streamPreservesWhitespaceChunksForMarkdownSyntax() {
        SpringAiChatRuntime runtime = new SpringAiChatRuntime(
                "test",
                new StreamingWhitespaceChatModel()
        );

        String answer = String.join("", runtime.stream(new Prompt("test")).collectList().block());

        assertThat(answer).isEqualTo("### 二、标题\n- 列表项");
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static final class StreamingWhitespaceChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return chunk("done");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(
                    chunk("###"),
                    chunk(" "),
                    chunk("二、标题"),
                    chunk("\n"),
                    chunk("-"),
                    chunk(" "),
                    chunk("列表项")
            );
        }
    }
}
