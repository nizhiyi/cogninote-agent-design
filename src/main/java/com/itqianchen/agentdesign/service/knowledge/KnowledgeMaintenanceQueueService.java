package com.itqianchen.agentdesign.service.knowledge;

import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRun;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunOperation;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunStatus;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeMaintenanceException;
import com.itqianchen.agentdesign.domain.ingestion.DocumentParseException;
import com.itqianchen.agentdesign.domain.ingestion.DocumentIdentity;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderRebuildResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderRunResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeMaintenanceQueueResponse;
import com.itqianchen.agentdesign.repository.knowledge.KnowledgeFolderRunRepository;
import com.itqianchen.agentdesign.service.index.IndexService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识库维护任务的唯一调度者。
 *
 * <p>知识库维护会同时写 SQLite、Lucene 和图谱派生数据；这里使用单 worker FIFO 队列，牺牲并发换取
 * 本地桌面应用最重要的一致性和可解释状态。所有维护按钮都应先入队，再由该服务串行执行。</p>
 */
@Service
public class KnowledgeMaintenanceQueueService implements ApplicationListener<ApplicationReadyEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeMaintenanceQueueService.class);

    private final KnowledgeFolderRunRepository runRepository;
    private final KnowledgeFolderService folderService;
    private final IndexService indexService;
    private final KnowledgeFolderRunService runService;
    private final KnowledgeMaintenanceRunPublisher publisher;
    private final DocumentIdentity documentIdentity;
    private final TaskExecutor taskExecutor;
    private final Map<String, MaintenanceTaskRequest> taskRequests = new ConcurrentHashMap<>();
    private boolean workerRunning;

    public KnowledgeMaintenanceQueueService(
            KnowledgeFolderRunRepository runRepository,
            KnowledgeFolderService folderService,
            IndexService indexService,
            KnowledgeFolderRunService runService,
            KnowledgeMaintenanceRunPublisher publisher,
            DocumentIdentity documentIdentity,
            TaskExecutor taskExecutor
    ) {
        this.runRepository = runRepository;
        this.folderService = folderService;
        this.indexService = indexService;
        this.runService = runService;
        this.publisher = publisher;
        this.documentIdentity = documentIdentity;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int cleaned = runRepository.cleanupInterruptedRuns();
        if (cleaned > 0) {
            log.info("knowledge_maintenance_interrupted_runs_cleaned count={}", cleaned);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    public KnowledgeFolderRunResponse enqueueImport(String folderPath, boolean recursive) {
        Path folder = Path.of(folderPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(folder)) {
            throw new DocumentParseException("Folder does not exist or is not a directory: " + folder);
        }
        String normalizedPath = folder.toString();
        String folderId = documentIdentity.idForPath(normalizedPath);
        return enqueue(new MaintenanceTaskRequest(
                KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER,
                folderId,
                KnowledgeFolderRunOperation.IMPORT,
                normalizedPath,
                recursive,
                null
        ));
    }

    public KnowledgeFolderRunResponse enqueueRebuildAllIndex() {
        return enqueue(new MaintenanceTaskRequest(
                KnowledgeFolderRunScopeType.ALL,
                null,
                KnowledgeFolderRunOperation.REBUILD_INDEX,
                null,
                true,
                null
        ));
    }

    public KnowledgeFolderRunResponse enqueueFolderSync(String folderId) {
        return enqueue(folderTask(folderId, KnowledgeFolderRunOperation.SYNC));
    }

    public KnowledgeFolderRunResponse enqueueFolderRebuild(String folderId) {
        return enqueue(folderTask(folderId, KnowledgeFolderRunOperation.REBUILD_INDEX));
    }

    public KnowledgeFolderRunResponse enqueueFolderEnabled(String folderId, boolean enabled) {
        return enqueue(new MaintenanceTaskRequest(
                KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER,
                folderId,
                enabled ? KnowledgeFolderRunOperation.ENABLE : KnowledgeFolderRunOperation.DISABLE,
                null,
                true,
                enabled
        ));
    }

    public KnowledgeFolderRunResponse enqueueFolderDelete(String folderId) {
        return enqueue(folderTask(folderId, KnowledgeFolderRunOperation.DELETE));
    }

    public KnowledgeMaintenanceQueueResponse queue() {
        return new KnowledgeMaintenanceQueueResponse(
                runRepository.findActiveRuns().stream().map(KnowledgeFolderRunResponse::from).toList(),
                withQueuePositions(runRepository.findQueuedRuns()),
                runRepository.findLatestRun().map(KnowledgeFolderRunResponse::from).orElse(null)
        );
    }

    public KnowledgeFolderRunResponse getRun(String runId) {
        return runRepository.findById(runId)
                .map(KnowledgeFolderRunResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge maintenance run not found: " + runId));
    }

    public SseEmitter subscribe(String runId) {
        KnowledgeFolderRunResponse snapshot = getRun(runId);
        boolean terminal = isTerminal(snapshot.status());
        return publisher.subscribe(runId, snapshot, terminal);
    }

    public boolean cancel(String runId) {
        KnowledgeFolderRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge maintenance run not found: " + runId));
        if (run.status() == KnowledgeFolderRunStatus.QUEUED) {
            taskRequests.remove(runId);
            runRepository.markCancelled(runId, "用户取消排队中的维护任务。");
            runRepository.findById(runId).map(KnowledgeFolderRunResponse::from)
                    .ifPresent(response -> publisher.publishCancelled(runId, response));
            publisher.publishQueueUpdated(queue());
            return true;
        }
        throw new KnowledgeMaintenanceException("只能取消等待中的维护任务；正在运行的任务会自动执行到安全完成点。");
    }

    private synchronized KnowledgeFolderRunResponse enqueue(MaintenanceTaskRequest request) {
        return runRepository.findActiveByScopeAndOperation(request.scopeType(), request.scopeId(), request.operation())
                .map(KnowledgeFolderRunResponse::from)
                .orElseGet(() -> createQueuedRun(request));
    }

    private KnowledgeFolderRunResponse createQueuedRun(MaintenanceTaskRequest request) {
        long now = System.currentTimeMillis();
        KnowledgeFolderRun run = new KnowledgeFolderRun(
                UUID.randomUUID().toString(),
                request.scopeType(),
                request.scopeId(),
                request.operation(),
                KnowledgeFolderRunStatus.QUEUED,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                "QUEUED",
                0,
                0,
                currentItem(request),
                now,
                null,
                null,
                null,
                null,
                now,
                now
        );
        runRepository.insert(run);
        taskRequests.put(run.id(), request);
        KnowledgeFolderRunResponse response = KnowledgeFolderRunResponse.from(run);
        publisher.publishQueued(run.id(), response);
        dispatchNext();
        return response;
    }

    private synchronized void dispatchNext() {
        if (workerRunning) {
            return;
        }
        List<KnowledgeFolderRun> queuedRuns = runRepository.findQueuedRuns();
        if (queuedRuns.isEmpty()) {
            publisher.publishQueueUpdated(queue());
            return;
        }
        KnowledgeFolderRun next = queuedRuns.getFirst();
        MaintenanceTaskRequest request = taskRequests.get(next.id());
        if (request == null) {
            runRepository.markCancelled(next.id(), "任务参数已丢失，维护任务已取消。");
            dispatchNext();
            return;
        }
        workerRunning = true;
        taskExecutor.execute(() -> runTask(next.id(), request));
    }

    private void runTask(String runId, MaintenanceTaskRequest request) {
        try {
            long startedAt = System.currentTimeMillis();
            runRepository.markStarted(runId, phaseFor(request.operation()), 1, currentItem(request), startedAt);
            runRepository.findById(runId).map(KnowledgeFolderRunResponse::from)
                    .ifPresent(response -> publisher.publishStarted(runId, response));
            ensureNotCancelledBeforeSideEffects(runId);
            KnowledgeMaintenanceCompletion completion = execute(request, runId);
            completeRun(runId, completion, request);
        } catch (CancelledMaintenanceRunException ex) {
            runRepository.markCancelled(runId, ex.getMessage());
            runRepository.findById(runId).map(KnowledgeFolderRunResponse::from)
                    .ifPresent(response -> publisher.publishCancelled(runId, response));
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            runRepository.markFailed(runId, message);
            runRepository.findById(runId).map(KnowledgeFolderRunResponse::from)
                    .ifPresent(response -> publisher.publishFailed(runId, response));
            log.warn("knowledge_maintenance_run_failed runId={} operation={} scopeType={} scopeId={} reason={}",
                    runId,
                    request.operation(),
                    request.scopeType(),
                    request.scopeId(),
                    message
            );
            log.debug("knowledge_maintenance_run_failed_stacktrace runId={}", runId, ex);
        } finally {
            taskRequests.remove(runId);
            publisher.clearCancellation(runId);
            synchronized (this) {
                workerRunning = false;
                dispatchNext();
            }
        }
    }

    private KnowledgeMaintenanceCompletion execute(MaintenanceTaskRequest request, String runId) {
        return switch (request.operation()) {
            case IMPORT -> importFolder(request);
            case SYNC -> syncFolder(request);
            case REBUILD_INDEX -> request.scopeType() == KnowledgeFolderRunScopeType.ALL
                    ? rebuildAllIndex()
                    : rebuildFolder(request);
            case ENABLE, DISABLE -> setFolderEnabled(request);
            case DELETE -> deleteFolder(request);
        };
    }

    private KnowledgeMaintenanceCompletion importFolder(MaintenanceTaskRequest request) {
        IngestDocumentsResponse response = runService.withoutRecording(
                () -> folderService.importFolder(request.folderPath(), request.recursive())
        );
        return ingestCompletion(response);
    }

    private KnowledgeMaintenanceCompletion syncFolder(MaintenanceTaskRequest request) {
        IngestDocumentsResponse response = runService.withoutRecording(
                () -> folderService.syncFolder(request.scopeId())
        );
        return ingestCompletion(response);
    }

    private KnowledgeMaintenanceCompletion rebuildFolder(MaintenanceTaskRequest request) {
        KnowledgeFolderRebuildResponse response = runService.withoutRecording(
                () -> folderService.rebuildFolder(request.scopeId())
        );
        return new KnowledgeMaintenanceCompletion(
                statusFor(response.failedCount(), response.failedDocumentCount()),
                response.scannedCount(),
                response.parsedCount(),
                response.skippedCount(),
                response.failedCount(),
                response.indexedDocumentCount(),
                response.indexedChunkCount(),
                response.failedDocumentCount(),
                null,
                Math.max(1, response.scannedCount())
        );
    }

    private KnowledgeMaintenanceCompletion rebuildAllIndex() {
        RebuildIndexResponse response = runService.withoutRecording(indexService::rebuild);
        return new KnowledgeMaintenanceCompletion(
                statusFor(0, response.failedDocumentCount()),
                0,
                0,
                0,
                0,
                response.indexedDocumentCount(),
                response.indexedChunkCount(),
                response.failedDocumentCount(),
                null,
                Math.max(1, response.indexedDocumentCount())
        );
    }

    private KnowledgeMaintenanceCompletion setFolderEnabled(MaintenanceTaskRequest request) {
        runService.withoutRecording(() -> folderService.setEnabled(request.scopeId(), Boolean.TRUE.equals(request.enabled())));
        return KnowledgeMaintenanceCompletion.simple();
    }

    private KnowledgeMaintenanceCompletion deleteFolder(MaintenanceTaskRequest request) {
        runService.withoutRecording(() -> folderService.deleteFolder(request.scopeId()));
        return KnowledgeMaintenanceCompletion.simple();
    }

    private void completeRun(String runId, KnowledgeMaintenanceCompletion completion, MaintenanceTaskRequest request) {
        runRepository.markCompleted(
                runId,
                completion.status(),
                completion.scannedCount(),
                completion.parsedCount(),
                completion.skippedCount(),
                completion.failedCount(),
                completion.indexedDocumentCount(),
                completion.indexedChunkCount(),
                completion.failedDocumentCount(),
                completion.failuresJson(),
                completion.progressTotal(),
                completion.progressTotal()
        );
        runRepository.findById(runId).map(KnowledgeFolderRunResponse::from)
                .ifPresentOrElse(
                        response -> publisher.publishCompleted(runId, response),
                        () -> publisher.publishCompleted(runId, Map.of(
                                "runId", runId,
                                "operation", request.operation().name(),
                                "status", completion.status().name()
                        ))
                );
    }

    private void ensureNotCancelledBeforeSideEffects(String runId) {
        /*
         * 这里只处理历史兼容或极短窗口内的取消标记。
         * 用户入口只允许取消 QUEUED，避免运行中任务被误认为可以随时安全中断。
         */
        if (publisher.isCancelled(runId)) {
            throw new CancelledMaintenanceRunException("用户取消维护任务。");
        }
    }

    private static MaintenanceTaskRequest folderTask(String folderId, KnowledgeFolderRunOperation operation) {
        return new MaintenanceTaskRequest(
                KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER,
                folderId,
                operation,
                null,
                true,
                null
        );
    }

    private static KnowledgeMaintenanceCompletion ingestCompletion(IngestDocumentsResponse response) {
        return new KnowledgeMaintenanceCompletion(
                statusFor(response.failedCount(), 0),
                response.scannedCount(),
                response.parsedCount(),
                response.skippedCount(),
                response.failedCount(),
                0,
                0,
                0,
                null,
                Math.max(1, response.scannedCount())
        );
    }

    private static KnowledgeFolderRunStatus statusFor(long failedCount, long failedDocumentCount) {
        if (failedCount > 0 || failedDocumentCount > 0) {
            return KnowledgeFolderRunStatus.COMPLETED_WITH_WARNINGS;
        }
        return KnowledgeFolderRunStatus.COMPLETED;
    }

    private static String phaseFor(KnowledgeFolderRunOperation operation) {
        return switch (operation) {
            case IMPORT -> "IMPORTING";
            case SYNC -> "SYNCING";
            case REBUILD_INDEX -> "INDEXING";
            case ENABLE -> "ENABLING";
            case DISABLE -> "DISABLING";
            case DELETE -> "DELETING";
        };
    }

    private static String currentItem(MaintenanceTaskRequest request) {
        if (request.folderPath() != null && !request.folderPath().isBlank()) {
            return request.folderPath();
        }
        if (request.scopeType() == KnowledgeFolderRunScopeType.ALL) {
            return "全库";
        }
        return request.scopeId();
    }

    private static List<KnowledgeFolderRunResponse> withQueuePositions(List<KnowledgeFolderRun> runs) {
        int[] position = {1};
        return runs.stream()
                .map(run -> KnowledgeFolderRunResponse.from(run).withQueuePosition(position[0]++))
                .toList();
    }

    private static boolean isTerminal(KnowledgeFolderRunStatus status) {
        return status != KnowledgeFolderRunStatus.QUEUED
                && status != KnowledgeFolderRunStatus.RUNNING
                && status != KnowledgeFolderRunStatus.CANCELLING;
    }

    private record MaintenanceTaskRequest(
            KnowledgeFolderRunScopeType scopeType,
            String scopeId,
            KnowledgeFolderRunOperation operation,
            String folderPath,
            boolean recursive,
            Boolean enabled
    ) {
    }

    private static final class CancelledMaintenanceRunException extends RuntimeException {
        private CancelledMaintenanceRunException(String message) {
            super(message);
        }
    }
}
