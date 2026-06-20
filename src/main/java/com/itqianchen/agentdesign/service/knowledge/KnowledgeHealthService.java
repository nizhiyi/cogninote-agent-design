package com.itqianchen.agentdesign.service.knowledge;

import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.domain.document.DocumentStatus;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolder;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRun;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderSummary;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeHealthIssueCode;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeHealthStatus;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderHealthResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderHealthSummaryResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderRunResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeHealthIssueResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeHealthResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeHealthSummaryResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeProblemDocumentResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.repository.knowledge.KnowledgeFolderRepository;
import com.itqianchen.agentdesign.repository.knowledge.KnowledgeFolderRunRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计算知识库健康快照。
 *
 * <p>健康状态由 SQLite 文档事实、目录配置和轻量文件系统探针即时派生，不额外持久化副本。</p>
 */
@Service
public class KnowledgeHealthService {

    private static final int DETAIL_RUN_LIMIT = 20;

    private final KnowledgeFolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final KnowledgeFolderRunRepository runRepository;

    /**
     * 注入知识库健康诊断依赖。
     *
     * @param folderRepository 知识库目录仓储
     * @param documentRepository 文档仓储
     * @param runRepository 维护运行记录仓储
     */
    public KnowledgeHealthService(
            KnowledgeFolderRepository folderRepository,
            DocumentRepository documentRepository,
            KnowledgeFolderRunRepository runRepository
    ) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.runRepository = runRepository;
    }

    /**
     * 计算全库健康快照。
     *
     * @return 全库健康响应
     */
    @Transactional(readOnly = true)
    public KnowledgeHealthResponse health() {
        List<KnowledgeFolderSummary> summaries = folderRepository.findAllSummaries();
        Map<String, KnowledgeFolderRun> latestRuns = latestRunsByScope();
        List<FolderHealthSnapshot> folderSnapshots = summaries.stream()
                .map(summary -> folderSnapshot(summary, latestRuns.get(scopeKey(summary.folder().id()))))
                .toList();

        KnowledgeHealthSummaryResponse summary = totalSummary(folderSnapshots);
        List<KnowledgeHealthIssueResponse> issues = aggregateIssues(folderSnapshots);
        KnowledgeHealthStatus status = overallStatus(folderSnapshots, summary, issues);
        List<KnowledgeFolderHealthSummaryResponse> folders = folderSnapshots.stream()
                .map(FolderHealthSnapshot::toSummaryResponse)
                .toList();

        return new KnowledgeHealthResponse(status, summary, issues, folders);
    }

    /**
     * 计算单个目录健康详情。
     *
     * @param folderId 知识库目录 ID
     * @return 目录健康响应
     */
    @Transactional(readOnly = true)
    public KnowledgeFolderHealthResponse folderHealth(String folderId) {
        KnowledgeFolderSummary summary = folderRepository.findAllSummaries().stream()
                .filter(candidate -> candidate.folder().id().equals(folderId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge folder not found: " + folderId));

        List<KnowledgeDocument> documents = documentRepository.findByKnowledgeFolderIdOrderByUpdatedAtDesc(folderId);
        FolderHealthProbe probe = probeDocuments(documents);
        List<KnowledgeHealthIssueResponse> issues = folderIssues(summary, probe);
        KnowledgeHealthStatus status = folderStatus(summary, probe, issues);
        List<KnowledgeFolderRunResponse> runs = runRepository.findRuns(
                        KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER,
                        folderId,
                        DETAIL_RUN_LIMIT
                )
                .stream()
                .map(KnowledgeFolderRunResponse::from)
                .toList();

        return new KnowledgeFolderHealthResponse(
                folderId,
                status,
                issues,
                problemDocuments(documents, DocumentStatus.FAILED, "解析失败，请检查文件是否损坏或是否包含可读取文本。"),
                unindexedDocuments(documents),
                probe.missingLocalFiles(),
                probe.staleLocalFiles(),
                runs
        );
    }

    /**
     * 查询维护运行记录。
     *
     * @param scopeType 范围类型；为空时查询全部
     * @param scopeId 范围 ID；全库为空
     * @param limit 最大返回数量
     * @return 运行记录响应
     */
    @Transactional(readOnly = true)
    public List<KnowledgeFolderRunResponse> runs(KnowledgeFolderRunScopeType scopeType, String scopeId, Integer limit) {
        return runRepository.findRuns(scopeType, scopeId, limit)
                .stream()
                .map(KnowledgeFolderRunResponse::from)
                .toList();
    }

    private Map<String, KnowledgeFolderRun> latestRunsByScope() {
        Map<String, KnowledgeFolderRun> runs = new LinkedHashMap<>();
        for (KnowledgeFolderRun run : runRepository.findLatestRunsByScope()) {
            if (run.scopeType() == KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER && run.scopeId() != null) {
                runs.put(scopeKey(run.scopeId()), run);
            }
        }
        return runs;
    }

    private FolderHealthSnapshot folderSnapshot(KnowledgeFolderSummary summary, KnowledgeFolderRun latestRun) {
        List<KnowledgeDocument> documents = documentRepository.findByKnowledgeFolderIdOrderByUpdatedAtDesc(
                summary.folder().id()
        );
        FolderHealthProbe probe = probeDocuments(documents);
        List<KnowledgeHealthIssueResponse> issues = folderIssues(summary, probe);
        return new FolderHealthSnapshot(
                summary,
                folderStatus(summary, probe, issues),
                probe.missingLocalFiles().size(),
                probe.staleLocalFiles().size(),
                latestRun == null ? null : KnowledgeFolderRunResponse.from(latestRun),
                issues
        );
    }

    private FolderHealthProbe probeDocuments(List<KnowledgeDocument> documents) {
        List<KnowledgeProblemDocumentResponse> missingLocalFiles = new ArrayList<>();
        List<KnowledgeProblemDocumentResponse> staleLocalFiles = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            Path path = Path.of(document.sourcePath());
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                missingLocalFiles.add(KnowledgeProblemDocumentResponse.from(document, "本地文件不存在，点击同步可清理应用内记录。"));
                continue;
            }
            if (isStale(document, path)) {
                staleLocalFiles.add(KnowledgeProblemDocumentResponse.from(document, "本地文件大小或修改时间已变化，建议同步目录。"));
            }
        }
        return new FolderHealthProbe(List.copyOf(missingLocalFiles), List.copyOf(staleLocalFiles));
    }

    private boolean isStale(KnowledgeDocument document, Path path) {
        try {
            return Files.size(path) != document.fileSize()
                    || Files.getLastModifiedTime(path).toMillis() != document.lastModified();
        } catch (IOException ex) {
            return true;
        }
    }

    private List<KnowledgeHealthIssueResponse> folderIssues(KnowledgeFolderSummary summary, FolderHealthProbe probe) {
        List<KnowledgeHealthIssueResponse> issues = new ArrayList<>();
        KnowledgeFolder folder = summary.folder();
        String folderId = folder.id();
        if (!folder.enabled()) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.DISABLED_FOLDER,
                    "WARNING",
                    "目录已停用，不会参与搜索和 RAG。",
                    "ENABLE",
                    folderId,
                    1
            ));
        }
        if (!Files.isDirectory(Path.of(folder.folderPath()))) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.FOLDER_NOT_FOUND,
                    "ERROR",
                    "本地目录当前不可访问，目录内资料无法确认是否最新。",
                    "DELETE_FOLDER",
                    folderId,
                    1
            ));
        }
        if (folder.enabled() && summary.documentCount() == 0) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.NO_DOCUMENTS,
                    "WARNING",
                    "目录中还没有文档记录。",
                    "SYNC_FOLDER",
                    folderId,
                    1
            ));
        }
        if (summary.failedCount() > 0) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.PARSE_FAILED,
                    "WARNING",
                    "有 " + summary.failedCount() + " 个文档解析失败，搜索和 RAG 可能缺失内容。",
                    "SYNC_FOLDER",
                    folderId,
                    summary.failedCount()
            ));
        }
        if (summary.unindexedCount() > 0) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.UNINDEXED_DOCUMENTS,
                    "ERROR",
                    "有 " + summary.unindexedCount() + " 个已解析文档尚未进入索引。",
                    "REBUILD_INDEX",
                    folderId,
                    summary.unindexedCount()
            ));
        }
        if (!probe.missingLocalFiles().isEmpty()) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.MISSING_LOCAL_FILES,
                    "WARNING",
                    "有 " + probe.missingLocalFiles().size() + " 个已记录文件在本地不存在。",
                    "SYNC_FOLDER",
                    folderId,
                    probe.missingLocalFiles().size()
            ));
        }
        if (!probe.staleLocalFiles().isEmpty()) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.STALE_LOCAL_FILES,
                    "WARNING",
                    "有 " + probe.staleLocalFiles().size() + " 个本地文件疑似已变化。",
                    "SYNC_FOLDER",
                    folderId,
                    probe.staleLocalFiles().size()
            ));
        }
        return List.copyOf(issues);
    }

    private KnowledgeHealthStatus folderStatus(
            KnowledgeFolderSummary summary,
            FolderHealthProbe probe,
            List<KnowledgeHealthIssueResponse> issues
    ) {
        if (!summary.folder().enabled()) {
            return KnowledgeHealthStatus.DISABLED;
        }
        if (hasSeverity(issues, "ERROR")) {
            return KnowledgeHealthStatus.ERROR;
        }
        if (summary.documentCount() == 0) {
            return KnowledgeHealthStatus.EMPTY;
        }
        if (hasSeverity(issues, "WARNING") || !probe.missingLocalFiles().isEmpty() || !probe.staleLocalFiles().isEmpty()) {
            return KnowledgeHealthStatus.WARNING;
        }
        return KnowledgeHealthStatus.HEALTHY;
    }

    private KnowledgeHealthSummaryResponse totalSummary(List<FolderHealthSnapshot> snapshots) {
        int folderCount = snapshots.size();
        int enabledFolderCount = 0;
        int documentCount = 0;
        int parsedCount = 0;
        int failedCount = 0;
        int unindexedCount = 0;
        int missingLocalFileCount = 0;
        int staleLocalFileCount = 0;
        int chunkCount = 0;
        Long lastIngestedAt = null;
        Long lastIndexedAt = null;

        for (FolderHealthSnapshot snapshot : snapshots) {
            KnowledgeFolderSummary summary = snapshot.summary();
            if (summary.folder().enabled()) {
                enabledFolderCount++;
            }
            documentCount += summary.documentCount();
            parsedCount += summary.parsedCount();
            failedCount += summary.failedCount();
            unindexedCount += summary.unindexedCount();
            missingLocalFileCount += snapshot.missingLocalFileCount();
            staleLocalFileCount += snapshot.staleLocalFileCount();
            chunkCount += summary.chunkCount();
            lastIngestedAt = maxNullable(lastIngestedAt, summary.folder().lastIngestedAt());
            lastIndexedAt = maxNullable(lastIndexedAt, summary.folder().lastIndexedAt());
        }

        return new KnowledgeHealthSummaryResponse(
                folderCount,
                enabledFolderCount,
                documentCount,
                parsedCount,
                failedCount,
                unindexedCount,
                missingLocalFileCount,
                staleLocalFileCount,
                chunkCount,
                lastIngestedAt,
                lastIndexedAt
        );
    }

    private List<KnowledgeHealthIssueResponse> aggregateIssues(List<FolderHealthSnapshot> snapshots) {
        Map<KnowledgeHealthIssueCode, IssueAggregate> aggregates = new LinkedHashMap<>();
        for (FolderHealthSnapshot snapshot : snapshots) {
            for (KnowledgeHealthIssueResponse issue : snapshot.issues()) {
                aggregates.computeIfAbsent(issue.code(), ignored -> new IssueAggregate(issue))
                        .add(issue);
            }
        }
        return aggregates.values().stream()
                .map(IssueAggregate::toResponse)
                .sorted(Comparator.comparing(KnowledgeHealthIssueResponse::severity))
                .toList();
    }

    private KnowledgeHealthStatus overallStatus(
            List<FolderHealthSnapshot> snapshots,
            KnowledgeHealthSummaryResponse summary,
            List<KnowledgeHealthIssueResponse> issues
    ) {
        if (summary.folderCount() == 0 || summary.documentCount() == 0) {
            if (hasSeverity(issues, "ERROR")) {
                return KnowledgeHealthStatus.ERROR;
            }
            return KnowledgeHealthStatus.EMPTY;
        }
        if (hasSeverity(issues, "ERROR")) {
            return KnowledgeHealthStatus.ERROR;
        }
        if (hasSeverity(issues, "WARNING")
                || snapshots.stream().anyMatch(snapshot -> snapshot.status() == KnowledgeHealthStatus.DISABLED)) {
            return KnowledgeHealthStatus.WARNING;
        }
        return KnowledgeHealthStatus.HEALTHY;
    }

    private List<KnowledgeProblemDocumentResponse> problemDocuments(
            List<KnowledgeDocument> documents,
            DocumentStatus status,
            String message
    ) {
        return documents.stream()
                .filter(document -> document.status() == status)
                .map(document -> KnowledgeProblemDocumentResponse.from(document, message))
                .toList();
    }

    private List<KnowledgeProblemDocumentResponse> unindexedDocuments(List<KnowledgeDocument> documents) {
        return documents.stream()
                .filter(document -> document.status() == DocumentStatus.PARSED)
                .filter(document -> document.indexedAt() == null)
                .map(document -> KnowledgeProblemDocumentResponse.from(document, "已解析但未进入索引，建议重建目录索引。"))
                .toList();
    }

    private static KnowledgeHealthIssueResponse issue(
            KnowledgeHealthIssueCode code,
            String severity,
            String message,
            String action,
            String scopeId,
            int count
    ) {
        return new KnowledgeHealthIssueResponse(
                code,
                severity,
                message,
                action,
                KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER,
                scopeId,
                count
        );
    }

    private static boolean hasSeverity(List<KnowledgeHealthIssueResponse> issues, String severity) {
        return issues.stream().anyMatch(issue -> severity.equals(issue.severity()));
    }

    private static Long maxNullable(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private static String scopeKey(String folderId) {
        return KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER.name() + ":" + folderId;
    }

    private record FolderHealthProbe(
            List<KnowledgeProblemDocumentResponse> missingLocalFiles,
            List<KnowledgeProblemDocumentResponse> staleLocalFiles
    ) {
    }

    private record FolderHealthSnapshot(
            KnowledgeFolderSummary summary,
            KnowledgeHealthStatus status,
            int missingLocalFileCount,
            int staleLocalFileCount,
            KnowledgeFolderRunResponse lastRun,
            List<KnowledgeHealthIssueResponse> issues
    ) {
        private KnowledgeFolderHealthSummaryResponse toSummaryResponse() {
            return new KnowledgeFolderHealthSummaryResponse(
                    summary.folder().id(),
                    summary.folder().displayName(),
                    summary.folder().folderPath(),
                    summary.folder().enabled(),
                    status,
                    summary.documentCount(),
                    summary.parsedCount(),
                    summary.failedCount(),
                    summary.unindexedCount(),
                    missingLocalFileCount,
                    staleLocalFileCount,
                    summary.chunkCount(),
                    summary.folder().lastIngestedAt(),
                    summary.folder().lastIndexedAt(),
                    lastRun
            );
        }
    }

    private static final class IssueAggregate {
        private final KnowledgeHealthIssueCode code;
        private final String severity;
        private final String message;
        private final String action;
        private int count;

        private IssueAggregate(KnowledgeHealthIssueResponse issue) {
            this.code = issue.code();
            this.severity = issue.severity();
            this.message = issue.message();
            this.action = issue.action();
            this.count = 0;
        }

        private void add(KnowledgeHealthIssueResponse issue) {
            this.count += issue.count();
        }

        private KnowledgeHealthIssueResponse toResponse() {
            return new KnowledgeHealthIssueResponse(
                    code,
                    severity,
                    aggregateMessage(code, count, message),
                    action,
                    KnowledgeFolderRunScopeType.ALL,
                    null,
                    count
            );
        }

        private static String aggregateMessage(KnowledgeHealthIssueCode code, int count, String fallback) {
            return switch (code) {
                case FOLDER_NOT_FOUND -> "有 " + count + " 个目录当前不可访问。";
                case NO_DOCUMENTS -> "有 " + count + " 个启用目录没有文档记录。";
                case PARSE_FAILED -> "有 " + count + " 个文档解析失败。";
                case UNINDEXED_DOCUMENTS -> "有 " + count + " 个已解析文档尚未进入索引。";
                case STALE_LOCAL_FILES -> "有 " + count + " 个本地文件疑似已变化。";
                case MISSING_LOCAL_FILES -> "有 " + count + " 个已记录文件在本地不存在。";
                case DISABLED_FOLDER -> "有 " + count + " 个目录已停用。";
                default -> Objects.requireNonNullElse(fallback, "知识库存在需要处理的问题。");
            };
        }
    }
}
