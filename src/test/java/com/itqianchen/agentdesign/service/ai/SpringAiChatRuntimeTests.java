package com.itqianchen.agentdesign.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Spring Ai Chat 运行时 测试 承担 聊天会话 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class SpringAiChatRuntimeTests {

    /**
     * 启动 stream Preserves Whitespace Chunks For Markdown Syntax 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    @Test
    void streamPreservesWhitespaceChunksForMarkdownSyntax() {
        SpringAiChatRuntime runtime = new SpringAiChatRuntime(
                "test",
                new StreamingWhitespaceChatModel()
        );

        String answer = String.join("", runtime.stream(new Prompt("test")).collectList().block());

        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(answer).isEqualTo("### 二、标题\n- 列表项");
    }

    /**
     * 启动 stream Fails When Provider Reports Length Finish Reason 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    @Test
    void streamFailsWhenProviderReportsLengthFinishReason() {
        SpringAiChatRuntime runtime = new SpringAiChatRuntime(
                "test",
                new TruncatedChatModel()
        );

        /**
         * 执行 聊天会话 中的 assert That Thrown By 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThatThrownBy(() -> runtime.stream(new Prompt("test")).collectList().block())
                .isInstanceOf(ChatCompletionIncompleteException.class)
                .hasMessageContaining("finishReason=length");
    }

    /**
     * 执行 聊天会话 中的 chat Client Stream Fails When Provider Reports Length Finish Reason 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chatClientStreamFailsWhenProviderReportsLengthFinishReason() {
        SpringAiChatRuntime runtime = new SpringAiChatRuntime(
                "test",
                new TruncatedChatModel()
        );

        /**
         * 执行 聊天会话 中的 assert That Thrown By 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThatThrownBy(() -> runtime.stream(
                        "system",
                        "user",
                        List.of(),
                        null
                ).collectList().block())
                .isInstanceOf(ChatCompletionIncompleteException.class)
                .hasMessageContaining("finishReason=length");
    }

    /**
     * 执行 聊天会话 中的 chunk 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /**
     * 执行 聊天会话 中的 finished Chunk 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ChatResponse finishedChunk(String text, String finishReason) {
        return new ChatResponse(List.of(new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finishReason).build()
        )));
    }

    /**
     * Streaming Whitespace Chat Model 承担 聊天会话 模块的主要职责。
     * <p>注释说明维护边界，不改变现有运行逻辑。</p>
     */
    private static final class StreamingWhitespaceChatModel implements ChatModel {
        /**
         * 执行一次同步 call 调用。
         * <p>外部模型响应会被转换为本地可处理的文本结果。</p>
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            return chunk("done");
        }

        /**
         * 启动 stream 流式流程。
         * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
         */
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(
                    chunk("###"),
                    chunk(" "),
                    chunk("二、标题"),
                    chunk("\n"),
                    chunk("-"),
                    chunk(" "),
                    /**
                     * 执行 聊天会话 中的 chunk 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    chunk("列表项")
            );
        }
    }

    /**
     * Truncated Chat Model 承担 聊天会话 模块的主要职责。
     * <p>注释说明维护边界，不改变现有运行逻辑。</p>
     */
    private static final class TruncatedChatModel implements ChatModel {
        /**
         * 执行一次同步 call 调用。
         * <p>外部模型响应会被转换为本地可处理的文本结果。</p>
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            return finishedChunk("partial", "length");
        }

        /**
         * 启动 stream 流式流程。
         * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
         */
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(
                    chunk("回答前半段"),
                    /**
                     * 执行 聊天会话 中的 finished Chunk 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    finishedChunk("", "length")
            );
        }
    }
}
