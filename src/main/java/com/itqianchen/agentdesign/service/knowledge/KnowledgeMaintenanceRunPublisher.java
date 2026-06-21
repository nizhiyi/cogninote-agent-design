package com.itqianchen.agentdesign.service.knowledge;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识库维护任务 SSE 发布器。
 *
 * <p>SSE 只负责把任务快照和队列变化推送给前端，不拥有任务执行权；开始、取消和完成必须由
 * KnowledgeMaintenanceQueueService 通过数据库状态变更表达。终态事件发送后只调用 complete()，
 * 避免在 text/event-stream 响应上触发全局 JSON 错误写回。</p>
 */
@Component
public class KnowledgeMaintenanceRunPublisher {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeMaintenanceRunPublisher.class);
    private static final long SSE_TIMEOUT_DISABLED = 0L;

    private final Map<String, Set<SseEmitter>> emittersByRunId = new ConcurrentHashMap<>();
    private final Set<String> cancelledRunIds = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe(String runId, Object initialSnapshot, boolean terminalSnapshot) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_DISABLED);
        Set<SseEmitter> emitters = emittersByRunId.computeIfAbsent(runId, ignored -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(runId, emitter));
        emitter.onTimeout(() -> remove(runId, emitter));
        emitter.onError(error -> {
            log.debug("knowledge_maintenance_sse_closed runId={}", runId, error);
            remove(runId, emitter);
        });
        sendOne(runId, emitter, "maintenance-run-snapshot", initialSnapshot);
        if (terminalSnapshot) {
            completeRun(runId);
        }
        return emitter;
    }

    public void cancel(String runId) {
        cancelledRunIds.add(runId);
        publish(runId, "maintenance-run-cancelling", Map.of("runId", runId));
    }

    public boolean isCancelled(String runId) {
        return cancelledRunIds.contains(runId);
    }

    public void clearCancellation(String runId) {
        cancelledRunIds.remove(runId);
    }

    public void publishQueued(String runId, Object data) {
        publish(runId, "maintenance-run-queued", data);
        publishQueueUpdated(data);
    }

    public void publishStarted(String runId, Object data) {
        publish(runId, "maintenance-run-started", data);
        publishQueueUpdated(data);
    }

    public void publishProgress(String runId, Object data) {
        publish(runId, "maintenance-run-progress", data);
    }

    public void publishQueueUpdated(Object data) {
        for (String runId : emittersByRunId.keySet()) {
            publish(runId, "maintenance-queue-updated", data);
        }
    }

    public void publishCompleted(String runId, Object data) {
        publish(runId, "maintenance-run-completed", data);
        completeRun(runId);
    }

    public void publishFailed(String runId, Object data) {
        publish(runId, "maintenance-run-failed", data);
        completeRun(runId);
    }

    public void publishCancelled(String runId, Object data) {
        publish(runId, "maintenance-run-cancelled", data);
        completeRun(runId);
    }

    private void publish(String runId, String eventName, Object data) {
        Set<SseEmitter> emitters = emittersByRunId.get(runId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            sendOne(runId, emitter, eventName, data);
        }
    }

    private void sendOne(String runId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException ex) {
            remove(runId, emitter);
            log.debug("knowledge_maintenance_sse_send_failed runId={} event={}", runId, eventName, ex);
            try {
                emitter.complete();
            } catch (IllegalStateException ignored) {
                // emitter 可能已被容器关闭。
            }
        }
    }

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
