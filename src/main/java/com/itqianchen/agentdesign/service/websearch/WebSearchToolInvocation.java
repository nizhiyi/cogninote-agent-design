package com.itqianchen.agentdesign.service.websearch;

import com.itqianchen.agentdesign.domain.dto.chat.ChatToolEvent;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * 本轮联网工具挂载结果。
 *
 * <p>包含传给模型的工具对象、传给工具的后端上下文，以及前端 SSE 观察工具事件的流。</p>
 */
public record WebSearchToolInvocation(
        List<Object> tools,
        Map<String, Object> toolContext,
        ToolExecutionCollector collector,
        Flux<ChatToolEvent> toolEvents
) {
    public static WebSearchToolInvocation disabled() {
        return new WebSearchToolInvocation(List.of(), Map.of(), null, Flux.empty());
    }

    public boolean enabled() {
        return collector != null;
    }
}
