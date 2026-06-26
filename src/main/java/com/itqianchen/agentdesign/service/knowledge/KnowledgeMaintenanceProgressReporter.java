package com.itqianchen.agentdesign.service.knowledge;

import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderRunResponse;
import com.itqianchen.agentdesign.repository.knowledge.KnowledgeFolderRunRepository;
import org.springframework.stereotype.Component;

/**
 * 维护任务线程内的轻量进度上报器。
 *
 * <p>Embedding 网关处在检索模块里，不应该知道当前执行的是哪个维护任务；队列执行前把 runId
 * 放进 ThreadLocal，外部调用或普通搜索没有上下文时上报会自动忽略。</p>
 */
@Component
public class KnowledgeMaintenanceProgressReporter {

    private final ThreadLocal<String> currentRunId = new ThreadLocal<>();
    private final KnowledgeFolderRunRepository runRepository;
    private final KnowledgeMaintenanceRunPublisher publisher;

    public KnowledgeMaintenanceProgressReporter(
            KnowledgeFolderRunRepository runRepository,
            KnowledgeMaintenanceRunPublisher publisher
    ) {
        this.runRepository = runRepository;
        this.publisher = publisher;
    }

    public <T> T withRun(String runId, MaintenanceOperation<T> operation) {
        String previousRunId = currentRunId.get();
        currentRunId.set(runId);
        try {
            return operation.execute();
        } finally {
            if (previousRunId == null) {
                currentRunId.remove();
            } else {
                currentRunId.set(previousRunId);
            }
        }
    }

    public void reportEmbeddingRateLimit(String message) {
        String runId = currentRunId.get();
        if (runId == null || runId.isBlank()) {
            return;
        }
        runRepository.findById(runId).ifPresent(run -> {
            runRepository.updateProgress(
                    runId,
                    "INDEXING",
                    run.progressCurrent(),
                    Math.max(1, run.progressTotal()),
                    message
            );
            runRepository.findById(runId)
                    .map(KnowledgeFolderRunResponse::from)
                    .ifPresent(response -> publisher.publishProgress(runId, response));
        });
    }

    @FunctionalInterface
    public interface MaintenanceOperation<T> {
        T execute();
    }
}
