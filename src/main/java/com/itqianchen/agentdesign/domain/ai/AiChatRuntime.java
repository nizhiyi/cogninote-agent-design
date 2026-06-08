package com.itqianchen.agentdesign.domain.ai;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

/**
 * Ai Chat 运行时 封装外部 聊天会话 调用。
 * <p>上层只依赖本地接口，不直接感知 Spring AI 或厂商 SDK 的细节。</p>
 */
public interface AiChatRuntime {

    /**
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    Flux<String> stream(Prompt prompt);

    /**
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    Flux<String> stream(String systemPrompt, String userMessage, List<Advisor> advisors, Map<String, Object> advisorParams);

    /**
     * 执行一次同步 call Text 调用。
     * <p>外部模型响应会被转换为本地可处理的文本结果。</p>
     */
    String callText(String systemPrompt, String userMessage);

    /**
     * 测试 test Connection 是否可用。
     * <p>使用最小请求验证配置、网络和模型服务是否连通。</p>
     */
    void testConnection(Prompt prompt);
}
