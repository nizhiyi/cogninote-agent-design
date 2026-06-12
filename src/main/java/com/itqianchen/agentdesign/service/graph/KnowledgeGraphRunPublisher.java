package com.itqianchen.agentdesign.service.graph;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识图谱 run 的 SSE 发布器。
 * <p>SSE 是观察通道，不拥有后台任务生命周期；取消必须通过显式 cancel 接口表达。</p>
 */
@Component
public class KnowledgeGraphRunPublisher {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphRunPublisher.class);
    private static final long SSE_TIMEOUT_DISABLED = 0L;

    private final Map<String, Set<SseEmitter>> emittersByRunId = new ConcurrentHashMap<>();
    private final Set<String> cancelledRunIds = ConcurrentHashMap.newKeySet();

    /**
     * 订阅指定图谱运行的 SSE 事件。
     *
     * @param runId 运行 ID
     * @param initialSnapshot 初始快照
     * @param terminalSnapshot 初始快照是否已经是终态
     * @return SSE emitter
     */
    public SseEmitter subscribe(String runId, Object initialSnapshot, boolean terminalSnapshot) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_DISABLED);
        Set<SseEmitter> emitters = emittersByRunId.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(error -> {
            log.debug("knowledge_graph_sse_closed runId={}", runId, error);
            remove(runId, emitter);
        });
        sendOne(runId, emitter, "graph-run-snapshot", initialSnapshot);
        if (terminalSnapshot) {
            completeRun(runId);
        }
        return emitter;
    }

    /**
     * 记录取消意图并通知订阅者。
     *
     * @param runId 运行 ID
     */
    public void cancel(String runId) {
        cancelledRunIds.add(runId);
        publish(runId, "graph-run-cancel-requested", Map.of("runId", runId));
    }

    /**
     * 判断运行是否已收到取消请求。
     *
     * @param runId 运行 ID
     * @return 是否取消
     */
    public boolean isCancelled(String runId) {
        return cancelledRunIds.contains(runId);
    }

    /**
     * 清理运行的取消标记。
     *
     * @param runId 运行 ID
     */
    public void clearCancellation(String runId) {
        cancelledRunIds.remove(runId);
    }

    /**
     * 发布运行开始事件。
     *
     * @param runId 运行 ID
     * @param progress 进度快照
     */
    public void publishStarted(String runId, KnowledgeGraphRunProgress progress) {
        publish(runId, "graph-run-started", progress);
    }

    /**
     * 发布运行进度事件。
     *
     * @param runId 运行 ID
     * @param progress 进度快照
     */
    public void publishProgress(String runId, KnowledgeGraphRunProgress progress) {
        publish(runId, "graph-run-progress", progress);
    }

    /**
     * 发布视图生成完成事件。
     *
     * @param runId 运行 ID
     * @param viewType 视图类型
     */
    public void publishViewReady(String runId, String viewType) {
        publish(runId, "graph-run-view-ready", Map.of("runId", runId, "viewType", viewType));
    }

    /**
     * 发布运行完成事件并关闭订阅。
     *
     * @param runId 运行 ID
     * @param data 完成事件数据
     */
    public void publishCompleted(String runId, Object data) {
        publish(runId, "graph-run-completed", data);
        completeRun(runId);
    }

    /**
     * 发布运行失败事件并关闭订阅。
     *
     * @param runId 运行 ID
     * @param data 失败事件数据
     */
    public void publishFailed(String runId, Object data) {
        publish(runId, "graph-run-failed", data);
        completeRun(runId);
    }

    /**
     * 发布运行取消事件并关闭订阅。
     *
     * @param runId 运行 ID
     * @param data 取消事件数据
     */
    public void publishCancelled(String runId, Object data) {
        publish(runId, "graph-run-cancelled", data);
        completeRun(runId);
    }

    /**
     * 向当前订阅者广播事件。
     *
     * @param runId 运行 ID
     * @param eventName SSE 事件名
     * @param data 事件数据
     */
    private void publish(String runId, String eventName, Object data) {
        Set<SseEmitter> emitters = emittersByRunId.get(runId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendOne(runId, emitter, eventName, data);
        }
    }

    /**
     * 向单个 emitter 发送事件。
     *
     * @param runId 运行 ID
     * @param emitter SSE emitter
     * @param eventName SSE 事件名
     * @param data 事件数据
     */
    private void sendOne(String runId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException ex) {
            remove(runId, emitter);
            log.debug("knowledge_graph_sse_send_failed runId={} event={}", runId, eventName, ex);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // emitter 可能已被容器关闭。
            }
        }
    }

    /**
     * 完成运行并关闭所有订阅者。
     *
     * @param runId 运行 ID
     */
    private void completeRun(String runId) {
        Set<SseEmitter> emitters = emittersByRunId.remove(runId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // emitter 可能已被容器关闭。
            }
        }
        clearCancellation(runId);
    }

    /**
     * 移除单个 emitter。
     *
     * @param runId 运行 ID
     * @param emitter SSE emitter
     */
    private void remove(String runId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByRunId.get(runId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByRunId.remove(runId, emitters);
        }
    }
}
