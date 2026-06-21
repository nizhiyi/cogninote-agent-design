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
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计算知识库健康快照。
 *
 * <p>健康状态由 SQLite 文档事实、目录配置和轻量文件系统探针即时派生，不额外持久化副本。</p>
 */
@Service
public class KnowledgeHealthService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeHealthService.class);

    private static final int DETAIL_RUN_LIMIT = 20;

    private final KnowledgeFolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final KnowledgeFolderRunRepository runRepository;
    private final KnowledgeStore knowledgeStore;

    /**
     * 注入知识库健康诊断依赖。
     *
     * @param folderRepository 知识库目录仓储
     * @param documentRepository 文档仓储
     * @param runRepository 维护运行记录仓储
     * @param knowledgeStore 检索索引边界
     */
    public KnowledgeHealthService(
            KnowledgeFolderRepository folderRepository,
            DocumentRepository documentRepository,
            KnowledgeFolderRunRepository runRepository,
            KnowledgeStore knowledgeStore
    ) {
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.runRepository = runRepository;
        this.knowledgeStore = knowledgeStore;
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

        IndexHealthSnapshot indexHealth = indexHealth(folderSnapshots);
        KnowledgeHealthSummaryResponse summary = totalSummary(folderSnapshots, indexHealth);
        List<KnowledgeHealthIssueResponse> issues = allIssues(folderSnapshots, summary, indexHealth);
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

        if (!summary.folder().enabled()) {
            return disabledFolderHealth(folderId);
        }

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
     * 返回停用目录的状态快照。
     *
     * <p>停用是用户主动把目录排除出检索范围，不代表知识库需要修复；因此不继续展开解析、索引和
     * 本地文件探针问题，避免旧文档状态污染全库健康判断。</p>
     *
     * @param folderId 知识库目录 ID
     * @return 停用目录健康响应
     */
    private KnowledgeFolderHealthResponse disabledFolderHealth(String folderId) {
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
                KnowledgeHealthStatus.DISABLED,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
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

    /**
     * 读取每个目录最近一次维护记录。
     *
     * <p>全库运行记录不直接挂到目录卡片上；目录卡片只展示自身 scope 的最近维护结果。</p>
     *
     * @return 以 scopeKey 为键的最近运行记录
     */
    private Map<String, KnowledgeFolderRun> latestRunsByScope() {
        Map<String, KnowledgeFolderRun> runs = new LinkedHashMap<>();
        for (KnowledgeFolderRun run : runRepository.findLatestRunsByScope()) {
            if (run.scopeType() == KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER && run.scopeId() != null) {
                runs.put(scopeKey(run.scopeId()), run);
            }
        }
        return runs;
    }

    /**
     * 计算目录在全库列表中的轻量健康快照。
     *
     * <p>快照只保留计数和最近运行记录，不携带完整问题文档列表，避免全库健康接口返回过大的响应。</p>
     *
     * @param summary 目录统计摘要
     * @param latestRun 目录最近一次维护记录；没有记录时为空
     * @return 目录健康快照
     */
    private FolderHealthSnapshot folderSnapshot(KnowledgeFolderSummary summary, KnowledgeFolderRun latestRun) {
        if (!summary.folder().enabled()) {
            return new FolderHealthSnapshot(
                    summary,
                    KnowledgeHealthStatus.DISABLED,
                    0,
                    0,
                    0,
                    latestRun == null ? null : KnowledgeFolderRunResponse.from(latestRun),
                    List.of()
            );
        }

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
                indexedChunkCount(documents),
                latestRun == null ? null : KnowledgeFolderRunResponse.from(latestRun),
                issues
        );
    }

    /**
     * 探测 SQLite 文档记录对应的本地文件状态。
     *
     * <p>健康诊断是只读、尽力而为的入口。单个文档路径损坏、权限异常或外置盘不可用时，
     * 应把该文档报告为不可访问，而不是让整个健康面板返回 500。</p>
     *
     * @param documents 目录下的文档记录
     * @return 文件缺失和疑似变化的探针结果
     */
    private FolderHealthProbe probeDocuments(List<KnowledgeDocument> documents) {
        List<KnowledgeProblemDocumentResponse> missingLocalFiles = new ArrayList<>();
        List<KnowledgeProblemDocumentResponse> staleLocalFiles = new ArrayList<>();
        for (KnowledgeDocument document : documents) {
            try {
                Path path = Path.of(document.sourcePath());
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    missingLocalFiles.add(KnowledgeProblemDocumentResponse.from(document, "本地文件不存在，点击同步可清理应用内记录。"));
                    continue;
                }
                if (isStale(document, path)) {
                    staleLocalFiles.add(KnowledgeProblemDocumentResponse.from(document, "本地文件大小或修改时间已变化，建议同步目录。"));
                }
            } catch (RuntimeException ex) {
                log.warn("knowledge_health_file_probe_failed documentId={} path={}",
                        document.id(),
                        document.sourcePath(),
                        ex
                );
                missingLocalFiles.add(KnowledgeProblemDocumentResponse.from(document, probeFailureMessage(ex)));
            }
        }
        return new FolderHealthProbe(List.copyOf(missingLocalFiles), List.copyOf(staleLocalFiles));
    }

    /**
     * 判断本地文件是否与上次解析记录不一致。
     *
     * <p>仅比较大小和修改时间，避免在健康查询里重新读取全文或计算 hash；需要精确收敛时由同步流程处理。</p>
     *
     * @param document SQLite 中保存的文档元数据
     * @param path 当前本地文件路径
     * @return 是否疑似变化
     */
    private boolean isStale(KnowledgeDocument document, Path path) {
        try {
            return Files.size(path) != document.fileSize()
                    || Files.getLastModifiedTime(path).toMillis() != document.lastModified();
        } catch (IOException ex) {
            // 无法读取文件元数据时按“疑似变化”处理，让用户通过同步重新收敛 SQLite 记录。
            return true;
        }
    }

    /**
     * 将文件探针异常转换为用户可读的问题说明。
     *
     * @param ex 路径解析或文件系统访问异常
     * @return 问题文档展示消息
     */
    private static String probeFailureMessage(RuntimeException ex) {
        String detail = ex.getMessage();
        if (detail == null || detail.isBlank()) {
            return "无法访问本地文件，请检查路径格式或文件权限。";
        }
        return "无法访问本地文件：" + detail;
    }

    /**
     * 根据目录统计和文件探针结果生成目录级问题列表。
     *
     * <p>每个问题都携带建议动作，前端只展示或映射到用户显式操作，不由健康查询自动修复。</p>
     *
     * @param summary 目录统计摘要
     * @param probe 文件系统探针结果
     * @return 目录级健康问题
     */
    private List<KnowledgeHealthIssueResponse> folderIssues(KnowledgeFolderSummary summary, FolderHealthProbe probe) {
        List<KnowledgeHealthIssueResponse> issues = new ArrayList<>();
        KnowledgeFolder folder = summary.folder();
        String folderId = folder.id();
        if (!folder.enabled()) {
            return List.of();
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

    /**
     * 计算单个目录的最终健康状态。
     *
     * <p>DISABLED 是用户主动排除目录的状态，不作为健康问题升级；ERROR 表示需要修复才能恢复可信检索；
     * EMPTY 只在启用且没有文档时出现。</p>
     *
     * @param summary 目录统计摘要
     * @param probe 文件系统探针结果
     * @param issues 已生成的问题列表
     * @return 目录健康状态
     */
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

    /**
     * 汇总全库健康统计。
     *
     * <p>lastIngestedAt 和 lastIndexedAt 使用所有目录中的最大值，表达“全库最近一次发生过的维护时间”。</p>
     *
     * @param snapshots 目录健康快照列表
     * @return 全库统计摘要
     */
    private KnowledgeHealthSummaryResponse totalSummary(
            List<FolderHealthSnapshot> snapshots,
            IndexHealthSnapshot indexHealth
    ) {
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
            if (!summary.folder().enabled()) {
                lastIngestedAt = maxNullable(lastIngestedAt, summary.folder().lastIngestedAt());
                lastIndexedAt = maxNullable(lastIndexedAt, summary.folder().lastIndexedAt());
                continue;
            }

            enabledFolderCount++;
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
                lastIndexedAt,
                indexHealth.luceneDocumentCount(),
                indexHealth.luceneChunkCount(),
                indexHealth.embeddingConfigured(),
                indexHealth.indexConsistent()
        );
    }

    /**
     * 读取 Lucene 和 Embedding 状态并与 SQLite 快照做一致性判断。
     *
     * <p>SQLite 仍是业务事实来源；Lucene 统计只用于发现索引目录损坏或漏写。</p>
     *
     * @param snapshots 当前目录健康快照
     * @return 索引健康快照
     */
    private IndexHealthSnapshot indexHealth(List<FolderHealthSnapshot> snapshots) {
        long expectedIndexedDocumentCount = 0;
        long expectedIndexedChunkCount = 0;

        for (FolderHealthSnapshot snapshot : snapshots) {
            if (!snapshot.summary().folder().enabled()) {
                continue;
            }
            expectedIndexedDocumentCount += Math.max(0, snapshot.summary().parsedCount() - snapshot.summary().unindexedCount());
            expectedIndexedChunkCount += snapshot.indexedChunkCount();
        }
        List<KnowledgeDocument> unassignedDocuments = documentRepository.findUnassignedOrderByUpdatedAtDesc();
        expectedIndexedDocumentCount += unassignedDocuments.stream()
                .filter(document -> document.status() == DocumentStatus.PARSED)
                .filter(document -> document.indexedAt() != null)
                .count();
        expectedIndexedChunkCount += indexedChunkCount(unassignedDocuments);

        try {
            IndexStatusResponse status = knowledgeStore.status();
            boolean consistent = status.indexedDocumentCount() == expectedIndexedDocumentCount
                    && status.indexedChunkCount() == expectedIndexedChunkCount;
            return new IndexHealthSnapshot(
                    status.indexedDocumentCount(),
                    status.indexedChunkCount(),
                    status.embeddingConfigured(),
                    consistent,
                    true,
                    null
            );
        } catch (RuntimeException ex) {
            log.warn("knowledge_health_index_probe_failed", ex);
            return new IndexHealthSnapshot(0, 0, false, false, false, ex.getMessage());
        }
    }

    /**
     * 将目录级问题按问题类型聚合为全库问题。
     *
     * <p>聚合后 scopeType 变为 ALL，scopeId 为空；具体目录仍可通过 folders 或目录详情查看。</p>
     *
     * @param snapshots 目录健康快照列表
     * @return 全库聚合问题列表
     */
    private List<KnowledgeHealthIssueResponse> allIssues(
            List<FolderHealthSnapshot> snapshots,
            KnowledgeHealthSummaryResponse summary,
            IndexHealthSnapshot indexHealth
    ) {
        List<KnowledgeHealthIssueResponse> issues = new ArrayList<>();
        issues.addAll(aggregateIssues(snapshots));
        issues.addAll(systemIssues(summary, indexHealth));
        return issues.stream()
                .sorted(Comparator.comparing(KnowledgeHealthIssueResponse::severity))
                .toList();
    }

    /**
     * 生成不属于单个目录的全局诊断问题。
     *
     * @param summary 全库统计摘要
     * @param indexHealth 索引健康快照
     * @return 全局问题列表
     */
    private List<KnowledgeHealthIssueResponse> systemIssues(
            KnowledgeHealthSummaryResponse summary,
            IndexHealthSnapshot indexHealth
    ) {
        List<KnowledgeHealthIssueResponse> issues = new ArrayList<>();
        if (!indexHealth.indexConsistent()) {
            String message = indexHealth.readable()
                    ? "Lucene 索引与 SQLite 文档记录不一致，搜索和 RAG 可能缺失内容。"
                    : "Lucene 索引状态读取失败，建议重建索引。";
            issues.add(issue(
                    KnowledgeHealthIssueCode.INDEX_INCONSISTENT,
                    "ERROR",
                    message,
                    "REBUILD_INDEX",
                    null,
                    1
            ));
        }
        if (summary.documentCount() > 0 && !indexHealth.embeddingConfigured()) {
            issues.add(issue(
                    KnowledgeHealthIssueCode.EMBEDDING_UNCONFIGURED,
                    "WARNING",
                    "尚未配置可用向量模型；向量或混合检索会降级为关键词检索。",
                    "CONFIGURE_EMBEDDING",
                    null,
                    1
            ));
        }
        return List.copyOf(issues);
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

    /**
     * 计算全库最终健康状态。
     *
     * <p>全库状态只评价启用目录的可检索语料；停用目录是用户主动排除的范围，不参与 WARNING/ERROR 升级。</p>
     *
     * @param snapshots 目录健康快照列表
     * @param summary 全库统计摘要
     * @param issues 全库聚合问题
     * @return 全库健康状态
     */
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
        if (hasSeverity(issues, "WARNING")) {
            return KnowledgeHealthStatus.WARNING;
        }
        return KnowledgeHealthStatus.HEALTHY;
    }

    /**
     * 按文档解析状态筛选问题文档。
     *
     * @param documents 目录下的文档记录
     * @param status 需要筛选的文档状态
     * @param message 展示给用户的处理建议
     * @return 问题文档列表
     */
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

    /**
     * 找出已经解析但尚未写入检索索引的文档。
     *
     * <p>只有 PARSED 文档才应进入索引，FAILED 文档会由解析失败问题单独展示。</p>
     *
     * @param documents 目录下的文档记录
     * @return 未索引文档列表
     */
    private List<KnowledgeProblemDocumentResponse> unindexedDocuments(List<KnowledgeDocument> documents) {
        return documents.stream()
                .filter(document -> document.status() == DocumentStatus.PARSED)
                .filter(document -> document.indexedAt() == null)
                .map(document -> KnowledgeProblemDocumentResponse.from(document, "已解析但未进入索引，建议重建目录索引。"))
                .toList();
    }

    /**
     * 统计已经完成索引的 SQLite chunk 数。
     *
     * <p>Lucene 一致性校验只能和已索引文档比较；未索引文档会单独形成 UNINDEXED_DOCUMENTS 问题。</p>
     *
     * @param documents 目录下的文档记录
     * @return 已索引文档包含的 chunk 数
     */
    private static int indexedChunkCount(List<KnowledgeDocument> documents) {
        return documents.stream()
                .filter(document -> document.status() == DocumentStatus.PARSED)
                .filter(document -> document.indexedAt() != null)
                .mapToInt(KnowledgeDocument::chunkCount)
                .sum();
    }

    /**
     * 构造目录范围的问题响应。
     *
     * @param code 问题类型
     * @param severity 严重级别，当前使用 ERROR/WARNING
     * @param message 用户可读说明
     * @param action 推荐维护动作
     * @param scopeId 目录 ID
     * @param count 受影响对象数量
     * @return 健康问题响应
     */
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
                scopeId == null ? KnowledgeFolderRunScopeType.ALL : KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER,
                scopeId,
                count
        );
    }

    /**
     * 判断问题列表是否包含指定严重级别。
     *
     * @param issues 问题列表
     * @param severity 严重级别
     * @return 是否存在该级别问题
     */
    private static boolean hasSeverity(List<KnowledgeHealthIssueResponse> issues, String severity) {
        return issues.stream().anyMatch(issue -> severity.equals(issue.severity()));
    }

    /**
     * 取两个可空时间戳中的较大值。
     *
     * @param left 左侧时间戳
     * @param right 右侧时间戳
     * @return 两者最大值；两者都为空时返回空
     */
    private static Long maxNullable(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    /**
     * 构造运行记录 scope 查找键。
     *
     * @param folderId 目录 ID
     * @return scopeType 和 scopeId 组合键
     */
    private static String scopeKey(String folderId) {
        return KnowledgeFolderRunScopeType.KNOWLEDGE_FOLDER.name() + ":" + folderId;
    }

    /**
     * 文件系统探针结果。
     *
     * <p>这里保存完整问题文档，目录详情直接展示；全库快照只取其中计数，避免响应过大。</p>
     */
    private record FolderHealthProbe(
            List<KnowledgeProblemDocumentResponse> missingLocalFiles,
            List<KnowledgeProblemDocumentResponse> staleLocalFiles
    ) {
    }

    /**
     * 单个目录的全库列表快照。
     *
     * <p>该快照位于领域统计和 API 响应之间，用于复用目录状态、计数、最近运行记录和问题聚合。</p>
     */
    private record FolderHealthSnapshot(
            KnowledgeFolderSummary summary,
            KnowledgeHealthStatus status,
            int missingLocalFileCount,
            int staleLocalFileCount,
            int indexedChunkCount,
            KnowledgeFolderRunResponse lastRun,
            List<KnowledgeHealthIssueResponse> issues
    ) {
        /**
         * 转换为全库健康列表中的目录摘要响应。
         *
         * @return 目录健康摘要
         */
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

    /**
     * Lucene 和 Embedding 的全局诊断快照。
     *
     * <p>readable=false 表示索引状态读取失败，此时 indexConsistent 固定为 false。</p>
     */
    private record IndexHealthSnapshot(
            long luceneDocumentCount,
            long luceneChunkCount,
            boolean embeddingConfigured,
            boolean indexConsistent,
            boolean readable,
            String errorMessage
    ) {
    }

    /**
     * 全库问题聚合器。
     *
     * <p>同一问题类型在多个目录出现时，只保留一条全库问题并累加 count；严重级别、动作和兜底文案
     * 取首个目录问题，要求同一 code 在 folderIssues 中保持一致配置。</p>
     */
    private static final class IssueAggregate {
        private final KnowledgeHealthIssueCode code;
        private final String severity;
        private final String message;
        private final String action;
        private int count;

        /**
         * 创建聚合器并继承单目录问题的展示配置。
         *
         * @param issue 单目录问题
         */
        private IssueAggregate(KnowledgeHealthIssueResponse issue) {
            this.code = issue.code();
            this.severity = issue.severity();
            this.message = issue.message();
            this.action = issue.action();
            this.count = 0;
        }

        /**
         * 累加同类问题的影响数量。
         *
         * @param issue 同一问题类型的目录问题
         */
        private void add(KnowledgeHealthIssueResponse issue) {
            this.count += issue.count();
        }

        /**
         * 转换为全库问题响应。
         *
         * @return 全库聚合问题
         */
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

        /**
         * 生成全库聚合文案。
         *
         * @param code 问题类型
         * @param count 汇总数量
         * @param fallback 未覆盖问题类型时使用的单目录文案
         * @return 全库问题文案
         */
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
