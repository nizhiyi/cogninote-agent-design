package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentEvent;
import com.itqianchen.agentdesign.dto.chat.ChatDeltaEvent;
import com.itqianchen.agentdesign.dto.chat.ChatDoneEvent;
import com.itqianchen.agentdesign.dto.chat.ChatErrorEvent;
import com.itqianchen.agentdesign.dto.chat.ChatMetaEvent;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ChatSseEventMapper {

    private static final Logger log = LoggerFactory.getLogger(ChatSseEventMapper.class);

    public void subscribe(SseEmitter emitter, AgentChatStream stream) {
        sendSafely(emitter, new AgentEvent.Meta(
                stream.conversationId(),
                stream.retrievalMode(),
                stream.sources()
        ));

        stream.answer().subscribe(
                text -> sendSafely(emitter, new AgentEvent.Delta(text)),
                error -> {
                    log.warn("agent_chat_stream_failed requestId={} conversationId={}",
                            stream.requestId(),
                            stream.conversationId(),
                            error
                    );
                    sendSafely(emitter, new AgentEvent.Error(error.getMessage()));
                    emitter.complete();
                },
                () -> {
                    sendSafely(emitter, new AgentEvent.Done(null));
                    emitter.complete();
                }
        );
    }

    private static void sendSafely(SseEmitter emitter, AgentEvent event) {
        try {
            send(emitter, event);
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private static void send(SseEmitter emitter, AgentEvent event) throws IOException {
        if (event instanceof AgentEvent.Meta meta) {
            emitter.send(SseEmitter.event()
                    .name("meta")
                    .data(new ChatMetaEvent(meta.conversationId(), meta.retrievalMode(), meta.sources())));
            return;
        }
        if (event instanceof AgentEvent.Delta delta) {
            emitter.send(SseEmitter.event()
                    .name("delta")
                    .data(new ChatDeltaEvent(delta.text())));
            return;
        }
        if (event instanceof AgentEvent.Done done) {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(new ChatDoneEvent(done.usage())));
            return;
        }
        if (event instanceof AgentEvent.Error error) {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(new ChatErrorEvent(error.message())));
        }
    }
}
