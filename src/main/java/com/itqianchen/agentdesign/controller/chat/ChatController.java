package com.itqianchen.agentdesign.controller.chat;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;
import com.itqianchen.agentdesign.service.agent.AgentExecutionService;
import com.itqianchen.agentdesign.service.chat.ChatSseEventMapper;
import com.itqianchen.agentdesign.service.chat.ChatStreamCancellationRegistry;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_DISABLED = 0L;

    private final AgentExecutionService agentExecutionService;
    private final ChatSseEventMapper chatSseEventMapper;
    private final ChatStreamCancellationRegistry cancellationRegistry;

    public ChatController(
            AgentExecutionService agentExecutionService,
            ChatSseEventMapper chatSseEventMapper,
            ChatStreamCancellationRegistry cancellationRegistry
    ) {
        this.agentExecutionService = agentExecutionService;
        this.chatSseEventMapper = chatSseEventMapper;
        this.cancellationRegistry = cancellationRegistry;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request) {
        // 对话模型可能输出很慢，固定总时长超时会截断完整答案。
        // Servlet 规范中 0 表示不启用 async timeout，连接生命周期交给模型完成或前端主动停止。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_DISABLED);
        // Controller 只负责 HTTP/SSE 适配；检索、Prompt 和模型流由 Agent 执行层处理。
        AgentChatStream stream = agentExecutionService.stream(request);
        chatSseEventMapper.subscribe(emitter, stream);
        return emitter;
    }

    @PostMapping("/stream/{requestId}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable String requestId) {
        // 只有用户显式停止才取消后端模型流；普通 SSE 连接断开不走这个接口。
        return ApiResponse.ok(cancellationRegistry.cancel(requestId));
    }
}


