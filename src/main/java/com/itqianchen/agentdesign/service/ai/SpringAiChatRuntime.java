package com.itqianchen.agentdesign.service.ai;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
// Spring AI ChatClient 负责把本地 Prompt 转为模型提供商请求。
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * Spring Ai Chat 运行时 封装外部 聊天会话 调用。
 * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
 */
final class SpringAiChatRuntime implements AiChatRuntime {

    private final String providerLabel;
    private final ChatModel chatModel;

    /**
     * 注入 SpringAiChatRuntime 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    SpringAiChatRuntime(String providerLabel, ChatModel chatModel) {
        this.providerLabel = providerLabel;
        this.chatModel = chatModel;
    }

    /**
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    @Override
    public Flux<String> stream(Prompt prompt) {
        return chatModel.stream(prompt)
                .concatMap(SpringAiChatRuntime::toTextStream);
    }

    /**
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    @Override
    public Flux<String> stream(
            String systemPrompt,
            String userMessage,
            List<Advisor> advisors,
            Map<String, Object> advisorParams
    ) {
        // Spring AI ChatClient 负责把本地 Prompt 转为模型提供商请求。
        ChatClient.ChatClientRequestSpec spec = ChatClient.builder(chatModel)
                .build()
                // Spring AI ChatClient 负责把本地 Prompt 转为模型提供商请求。
                .prompt()
                .system(systemPrompt)
                .user(userMessage);
        if ((advisors != null && !advisors.isEmpty()) || (advisorParams != null && !advisorParams.isEmpty())) {
            spec = spec.advisors(advisor -> {
                if (advisors != null && !advisors.isEmpty()) {
                    advisor.advisors(advisors);
                }
                if (advisorParams != null && !advisorParams.isEmpty()) {
                    advisor.params(advisorParams);
                }
            });
        }
        return spec.stream()
                .chatResponse()
                .concatMap(SpringAiChatRuntime::toTextStream);
    }

    /**
     * 执行一次同步 call Text 调用。
     * <p>外部模型响应会被转换为本地可处理的文本结果。</p>
     */
    @Override
    public String callText(String systemPrompt, String userMessage) {
        // Spring AI ChatClient 负责把本地 Prompt 转为模型提供商请求。
        ChatResponse response = ChatClient.builder(chatModel)
                .build()
                // Spring AI ChatClient 负责把本地 Prompt 转为模型提供商请求。
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .chatResponse();
        String text = extractText(response);
        return text == null ? "" : text;
    }

    /**
     * 测试 test Connection 是否可用。
     * <p>使用最小请求验证配置、网络和模型服务是否连通。</p>
     */
    @Override
    public void testConnection(Prompt prompt) {
        try {
            chatModel.call(prompt);
        } catch (RuntimeException ex) {
            throw new ModelConfigurationException(providerLabel + " connection failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 执行 聊天会话 中的 extract Text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        // Spring AI/OpenAI-compatible 流可能发出只含元数据的结束片段。
        // 但空格和换行可能是独立 chunk，Markdown 语法依赖这些空白，不能用 isBlank 过滤。
        return response.getResult().getOutput().getText();
    }

    /**
     * 执行 聊天会话 中的 to Text Stream 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static Flux<String> toTextStream(ChatResponse response) {
        String text = extractText(response);
        String finishReason = finishReason(response);
        if (!isIncompleteFinishReason(finishReason)) {
            return text == null || text.isEmpty() ? Flux.empty() : Flux.just(text);
        }

        ChatCompletionIncompleteException exception = new ChatCompletionIncompleteException(
                "模型回答被提前截断，finishReason=" + finishReason + "。请提高模型输出长度上限，或让模型继续回答。"
        );
        if (text == null || text.isEmpty()) {
            return Flux.error(exception);
        }
        // 某些 Provider 会把最后一点内容和截断原因放在同一个 chunk。
        // 先交付已收到的文字，再把本轮流标记为未完成，避免吞掉最后一段。
        return Flux.just(text).concatWith(Flux.error(exception));
    }

    /**
     * 执行 聊天会话 中的 finish Reason 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String finishReason(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }
        return response.getResult().getMetadata().getFinishReason();
    }

    /**
     * 判断 is Incomplete Finish Reason 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    private static boolean isIncompleteFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return false;
        }
        String normalized = finishReason.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("length")
                || normalized.equals("max_tokens")
                || normalized.equals("max_completion_tokens")
                || normalized.equals("content_filter");
    }
}
