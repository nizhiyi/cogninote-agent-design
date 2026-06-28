package com.itqianchen.agentdesign.service.websearch;

import com.itqianchen.agentdesign.domain.dto.chat.ChatToolEvent;
import com.itqianchen.agentdesign.domain.dto.chat.RagSourceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Sinks;

/**
 * 单次请求级工具执行收集器。
 *
 * <p>该对象随本轮 Chat 流创建和释放，不放入静态缓存，避免不同会话之间串数据。</p>
 */
public final class ToolExecutionCollector {

    private final String requestId;
    private final int maxCalls;
    private final int initialSourceCount;
    private final Sinks.Many<ChatToolEvent> sink;
    private final AtomicInteger callCount = new AtomicInteger();
    private final List<RagSourceResponse> webSources = new ArrayList<>();

    public ToolExecutionCollector(
            String requestId,
            int maxCalls,
            int initialSourceCount,
            Sinks.Many<ChatToolEvent> sink
    ) {
        this.requestId = requestId;
        this.maxCalls = maxCalls;
        this.initialSourceCount = initialSourceCount;
        this.sink = sink;
    }

    /**
     * 检查本轮工具调用次数是否仍在限制内。
     *
     * <p>计数必须在真正调用外部 API 前完成，防止模型循环调用造成不可控费用。</p>
     */
    public void checkCallLimit() {
        int calls = callCount.incrementAndGet();
        if (calls > maxCalls) {
            throw new IllegalStateException("本轮联网搜索调用次数已达到上限");
        }
    }

    /**
     * 记录工具调用结果并推送 SSE tool 事件。
     *
     * @param toolName 工具名
     * @param query 搜索 query
     * @param result 工具结果
     * @param durationMs 调用耗时
     */
    public synchronized void record(String toolName, String query, WebSearchToolResult result, long durationMs) {
        List<RagSourceResponse> addedSources = result.success()
                ? toSources(result.results())
                : List.of();
        webSources.addAll(addedSources);
        sink.tryEmitNext(new ChatToolEvent(
                requestId,
                toolName,
                query,
                result.success() ? "COMPLETED" : "FAILED",
                durationMs,
                result.message(),
                addedSources
        ));
    }

    /**
     * 返回本轮累计网页来源快照。
     *
     * <p>保存助手消息时读取该快照，与本地知识库来源合并落库。</p>
     */
    public synchronized List<RagSourceResponse> webSources() {
        return List.copyOf(webSources);
    }

    /**
     * 结束本轮工具事件流。
     *
     * <p>SSE 层会在模型流终止后清理订阅；这里显式完成 sink，避免后台保留无意义的事件流。</p>
     */
    public void complete() {
        sink.tryEmitComplete();
    }

    private List<RagSourceResponse> toSources(List<WebSearchResultItem> results) {
        List<RagSourceResponse> sources = new ArrayList<>();
        for (WebSearchResultItem result : results == null ? List.<WebSearchResultItem>of() : results) {
            if (result.url() == null || result.url().isBlank()) {
                continue;
            }
            sources.add(RagSourceResponse.web(
                    initialSourceCount + webSources.size() + sources.size() + 1,
                    WebSearchSourceIds.fromUrl(result.url()),
                    result.title(),
                    result.url(),
                    result.snippet(),
                    result.score(),
                    result.provider(),
                    result.publishedAt()
            ));
        }
        return sources;
    }
}
