package com.itqianchen.agentdesign.controller.chat;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;
import com.itqianchen.agentdesign.service.agent.AgentExecutionService;
import com.itqianchen.agentdesign.service.chat.ChatSseEventMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final AgentExecutionService agentExecutionService;
    private final ChatSseEventMapper chatSseEventMapper;

    public ChatController(AgentExecutionService agentExecutionService, ChatSseEventMapper chatSseEventMapper) {
        this.agentExecutionService = agentExecutionService;
        this.chatSseEventMapper = chatSseEventMapper;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // Controller 只负责 HTTP/SSE 适配；检索、Prompt 和模型流由 Agent 执行层处理。
        AgentChatStream stream = agentExecutionService.stream(request);
        chatSseEventMapper.subscribe(emitter, stream);
        return emitter;
    }
}


