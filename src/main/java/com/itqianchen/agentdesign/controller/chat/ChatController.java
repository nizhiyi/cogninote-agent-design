package com.itqianchen.agentdesign.controller.chat;

import com.itqianchen.agentdesign.domain.chat.RagChatStream;
import com.itqianchen.agentdesign.dto.chat.ChatDeltaEvent;
import com.itqianchen.agentdesign.dto.chat.ChatDoneEvent;
import com.itqianchen.agentdesign.dto.chat.ChatErrorEvent;
import com.itqianchen.agentdesign.dto.chat.ChatMetaEvent;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;
import com.itqianchen.agentdesign.service.chat.RagChatService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final RagChatService ragChatService;

    public ChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request) {
        // SSE 需要持续推送 meta/delta/done 事件，不能套普通 ApiResponse。
        // 初始 meta 先返回来源和实际检索模式，前端即可边接收答案边展示引用。
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        RagChatStream stream = ragChatService.stream(request);
        sendSafely(emitter, "meta", new ChatMetaEvent(
                stream.conversationId(),
                stream.retrievalMode(),
                stream.sources()
        ));

        stream.answer().subscribe(
                text -> sendSafely(emitter, "delta", new ChatDeltaEvent(text)),
                error -> {
                    log.warn("rag_chat_stream_failed conversationId={}", stream.conversationId(), error);
                    sendSafely(emitter, "error", new ChatErrorEvent(error.getMessage()));
                    emitter.complete();
                },
                () -> {
                    sendSafely(emitter, "done", new ChatDoneEvent(null));
                    emitter.complete();
                }
        );
        return emitter;
    }

    private static void sendSafely(SseEmitter emitter, String eventName, Object payload) {
        try {
            send(emitter, eventName, payload);
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private static void send(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload));
    }
}


