package com.itqianchen.agentdesign.service.graph;


import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphRunStatus;
import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphScopeType;
import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphViewType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.domain.entity.document.KnowledgeDocument;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEdge;
import com.itqianchen.agentdesign.domain.exception.graph.KnowledgeGraphException;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphRun;
import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphRunStatus;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphScope;
import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphScopeType;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphView;
import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphViewType;
import com.itqianchen.agentdesign.domain.entity.knowledge.KnowledgeFolder;
import com.itqianchen.agentdesign.domain.entity.model.ModelConfig;
import com.itqianchen.agentdesign.domain.vo.search.IndexedDocument;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphEvidenceResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphRunResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphStatusResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphSummaryResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphViewResponse;
import com.itqianchen.agentdesign.mapper.graph.KnowledgeGraphSummaryRow;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.repository.graph.KnowledgeGraphRepository;
import com.itqianchen.agentdesign.repository.knowledge.KnowledgeFolderRepository;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识图谱应用服务。
 * <p>负责 run 生命周期、scope 解析、后台任务编排和前端查询 API。</p>
 */
@Service
public class KnowledgeGraphService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);
    private static final int MAX_GRAPH_DESCRIPTION_LENGTH = 280;

    private final KnowledgeGraphRepository graphRepository;
    private final KnowledgeFolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final ModelConfigService modelConfigService;
    private final GraphExtractionService extractionService;
    private final GraphMergeService mergeService;
    private final KnowledgeGraphRunPublisher publisher;
    private final TaskExecutor taskExecutor;
    private final GraphCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;

    /**
     * 注入图谱应用服务依赖。
     *
     * @param graphRepository 图谱仓储
     * @param folderRepository 知识库目录仓储
     * @param documentRepository 文档仓储
     * @param modelConfigService 模型配置服务
     * @param extractionService 图谱抽取服务
     * @param mergeService 图谱合并服务
     * @param publisher 运行事件发布器
     * @param taskExecutor 后台任务执行器
     * @param canonicalizer 图谱规范化工具
     * @param objectMapper JSON 解析器
     */
    public KnowledgeGraphService(
            KnowledgeGraphRepository graphRepository,
            KnowledgeFolderRepository folderRepository,
            DocumentRepository documentRepository,
            ModelConfigService modelConfigService,
            GraphExtractionService extractionService,
            GraphMergeService mergeService,
            KnowledgeGraphRunPublisher publisher,
            TaskExecutor taskExecutor,
            GraphCanonicalizer canonicalizer,
            ObjectMapper objectMapper
    ) {
        this.graphRepository = graphRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.modelConfigService = modelConfigService;
        this.extractionService = extractionService;
        this.mergeService = mergeService;
        this.publisher = publisher;
        this.taskExecutor = taskExecutor;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询已生成图谱清单。
     *
     * <p>该方法只聚合已存在的 view 元数据和事实计数，不读取 payload_json。完整视图由前端点击具体
     * scope 后再通过 view 接口按需加载。</p>
     *
     * @return 已生成图谱摘要列表
     */
    public List<KnowledgeGraphSummaryResponse> listGeneratedGraphs() {
        return graphRepository.findGeneratedGraphSummaries().stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    /**
     * 为指定范围创建或复用图谱重建运行。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全库范围为空
     * @return 运行响应
     */
    public synchronized KnowledgeGraphRunResponse rebuild(String scopeType, String scopeId) {
        KnowledgeGraphScope scope = resolveScope(scopeType, scopeId);
        // 同一 scope 同时只允许一个活跃 run，避免后台任务互相覆盖派生图。
        return graphRepository.findActiveRun(scope)
                .map(KnowledgeGraphRunResponse::from)
                .orElseGet(() -> createAndStartRun(scope));
    }

    /**
     * 删除指定范围已生成的知识图谱。
     *
     * <p>该操作只删除可从抽取缓存重建的派生图和运行历史；目录、文档、chunk 以及 chunk 抽取缓存保留。</p>
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全库范围为空
     */
    @Transactional
    public synchronized void deleteGeneratedGraph(String scopeType, String scopeId) {
        KnowledgeGraphScope scope = resolveScopeForDeletion(scopeType, scopeId);
        if (graphRepository.findActiveRun(scope).isPresent()) {
            throw new KnowledgeGraphException("当前范围的知识图谱正在生成，请取消或等待完成后再删除。");
        }
        graphRepository.deleteGeneratedGraph(scope);
    }

    /**
     * 查询图谱运行。
     *
     * @param runId 运行 ID
     * @return 运行响应
     */
    public KnowledgeGraphRunResponse getRun(String runId) {
        return graphRepository.findRunById(runId)
                .map(KnowledgeGraphRunResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge graph run not found: " + runId));
    }

    /**
     * 订阅图谱运行事件。
     *
     * @param runId 运行 ID
     * @return SSE emitter
     */
    public SseEmitter subscribe(String runId) {
        KnowledgeGraphRunResponse snapshot = getRun(runId);
        boolean terminal = !"QUEUED".equals(snapshot.status()) && !"RUNNING".equals(snapshot.status());
        return publisher.subscribe(runId, snapshot, terminal);
    }

    /**
     * 请求取消图谱运行。
     *
     * @param runId 运行 ID
     * @return 是否接受取消
     */
    public boolean cancel(String runId) {
        KnowledgeGraphRun run = graphRepository.findRunById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge graph run not found: " + runId));
        if (run.status() != KnowledgeGraphRunStatus.QUEUED && run.status() != KnowledgeGraphRunStatus.RUNNING) {
            return false;
        }
        publisher.cancel(runId);
        return true;
    }

    /**
     * 查询指定范围的图谱状态。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全库范围为空
     * @return 状态响应
     */
    public KnowledgeGraphStatusResponse status(String scopeType, String scopeId) {
        KnowledgeGraphScope scope = resolveScope(scopeType, scopeId);
        KnowledgeGraphView mindmap = graphRepository.findView(scope, KnowledgeGraphViewType.MINDMAP.name()).orElse(null);
        KnowledgeGraphView graph = graphRepository.findView(scope, KnowledgeGraphViewType.GRAPH.name()).orElse(null);
        Long generatedAt = maxUpdatedAt(mindmap, graph);
        return new KnowledgeGraphStatusResponse(
                scope.scopeType().name(),
                scope.normalizedScopeId(),
                scope.displayName(),
                graphRepository.findLatestRunForScope(scope).map(KnowledgeGraphRunResponse::from).orElse(null),
                graphRepository.countNodesByScope(scope),
                graphRepository.countEdgesByScope(scope),
                mindmap != null,
                graph != null,
                generatedAt
        );
    }

    /**
     * 查询指定图谱视图。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全库范围为空
     * @param viewType 视图类型
     * @return 视图响应
     */
    public KnowledgeGraphViewResponse view(String scopeType, String scopeId, String viewType) {
        KnowledgeGraphScope scope = resolveScope(scopeType, scopeId);
        KnowledgeGraphViewType normalizedViewType = parseViewType(viewType);
        KnowledgeGraphView view = graphRepository.findView(scope, normalizedViewType.name())
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge graph view not found: " + normalizedViewType));
        try {
            JsonNode payload = objectMapper.readTree(view.payloadJson());
            JsonNode enrichedPayload = enrichGraphPayloadEdges(scope, normalizedViewType, payload);
            return KnowledgeGraphViewResponse.from(view, enrichedPayload);
        } catch (JsonProcessingException ex) {
            throw new KnowledgeGraphException("Knowledge graph view payload is corrupted", ex);
        }
    }

    /**
     * 为旧版 GRAPH 视图快照补充关系展示字段。
     *
     * <p>旧缓存里可能只有英文细关系码；读取时统一收敛到粗分类和中文谓词，避免前端继续展示英文关系。</p>
     *
     * @param scope 图谱范围
     * @param viewType 视图类型
     * @param payload 已解析的视图 payload
     * @return 可直接返回前端的 payload
     */
    private JsonNode enrichGraphPayloadEdges(
            KnowledgeGraphScope scope,
            KnowledgeGraphViewType viewType,
            JsonNode payload
    ) {
        if (viewType != KnowledgeGraphViewType.GRAPH || !(payload instanceof ObjectNode objectPayload)) {
            return payload;
        }
        JsonNode edgesNode = objectPayload.path("edges");
        if (!edgesNode.isArray() || edgesNode.size() == 0) {
            return objectPayload;
        }

        Map<String, KnowledgeGraphEdge> edgeById = graphRepository.findEdgesByScope(scope).stream()
                .collect(Collectors.toMap(
                        KnowledgeGraphEdge::id,
                        edge -> edge,
                        (left, ignored) -> left
                ));

        for (JsonNode edgeNode : edgesNode) {
            if (!(edgeNode instanceof ObjectNode edgePayload)) {
                continue;
            }
            String edgeId = edgePayload.path("id").asText("");
            KnowledgeGraphEdge edge = edgeById.get(edgeId);
            String relationType = hasNonBlankText(edgePayload, "label")
                    ? edgePayload.path("label").asText()
                    : edge == null ? null : edge.relationType();
            // 旧 GRAPH payload 的 label 可能是 USES 等细粒度英文码，返回前统一压回内部粗分类。
            edgePayload.put("label", canonicalizer.relationType(relationType));
            String displayLabel = hasNonBlankText(edgePayload, "displayLabel")
                    ? edgePayload.path("displayLabel").asText()
                    : edge == null ? null : edge.displayLabel();
            String normalizedDisplayLabel = canonicalizer.relationDisplayLabel(displayLabel);
            edgePayload.put("displayLabel", normalizedDisplayLabel);
            String description = hasNonBlankText(edgePayload, "description")
                    ? edgePayload.path("description").asText()
                    : edge == null ? null : edge.description();
            // 不用 source/target 节点 ID 拼描述；旧缓存没有展示名时，兜底句只保留中文关系谓词。
            edgePayload.put("description", canonicalizer.relationDescription(
                    payloadText(edgePayload, "sourceLabel"),
                    payloadText(edgePayload, "targetLabel"),
                    normalizedDisplayLabel,
                    description,
                    MAX_GRAPH_DESCRIPTION_LENGTH
            ));
        }
        return objectPayload;
    }

    private static boolean hasNonBlankText(ObjectNode node, String fieldName) {
        return node.hasNonNull(fieldName) && !node.path(fieldName).asText("").isBlank();
    }

    private static String payloadText(ObjectNode node, String fieldName) {
        return hasNonBlankText(node, fieldName) ? node.path(fieldName).asText() : "";
    }

    /**
     * 查询节点证据。
     *
     * @param nodeId 节点 ID
     * @return 证据响应列表
     */
    public List<KnowledgeGraphEvidenceResponse> nodeEvidence(String nodeId) {
        return graphRepository.findEvidenceByNodeId(nodeId).stream()
                .map(KnowledgeGraphEvidenceResponse::from)
                .toList();
    }

    /**
     * 查询边证据。
     *
     * @param edgeId 边 ID
     * @return 证据响应列表
     */
    public List<KnowledgeGraphEvidenceResponse> edgeEvidence(String edgeId) {
        return graphRepository.findEvidenceByEdgeId(edgeId).stream()
                .map(KnowledgeGraphEvidenceResponse::from)
                .toList();
    }

    /**
     * 创建运行记录并提交后台任务。
     *
     * @param scope 图谱范围
     * @return 新运行响应
     */
    private KnowledgeGraphRunResponse createAndStartRun(KnowledgeGraphScope scope) {
        // run 记录保存启动时的 Chat 配置 ID，后续缓存复用和 merge 都以这个快照判断。
        ModelConfig chatConfigSnapshot = modelConfigService.activeChatOrDefault();
        long now = System.currentTimeMillis();
        KnowledgeGraphRun run = new KnowledgeGraphRun(
                UUID.randomUUID().toString(),
                scope.scopeType(),
                scope.normalizedScopeId(),
                KnowledgeGraphRunStatus.QUEUED,
                chatConfigSnapshot.id(),
                extractionService.promptVersion(),
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                now,
                now
        );
        graphRepository.insertRun(run);
        try {
            taskExecutor.execute(() -> runGraphTask(run.id(), scope));
        } catch (RuntimeException ex) {
            graphRepository.markRunFailed(run.id(), "Failed to start graph run: " + ex.getMessage(), System.currentTimeMillis());
            throw ex;
        }
        return KnowledgeGraphRunResponse.from(run);
    }

    /**
     * 执行后台图谱抽取、合并和视图构建任务。
     *
     * @param runId 运行 ID
     * @param scope 图谱范围
     */
    private void runGraphTask(String runId, KnowledgeGraphScope scope) {
        publisher.clearCancellation(runId);
        try {
            graphRepository.deleteOrphanChunkExtractions();
            List<IndexedDocument> documents = documentsForScope(scope);
            int totalChunks = extractionService.countChunks(documents);
            long startedAt = System.currentTimeMillis();
            graphRepository.markRunStarted(runId, totalChunks, startedAt);
            publisher.publishStarted(runId, new KnowledgeGraphRunProgress(
                    runId,
                    KnowledgeGraphRunStatus.RUNNING.name(),
                    "EXTRACTING",
                    totalChunks,
                    0,
                    0,
                    0
            ));

            ModelConfig chatConfig = modelConfigService.requireActiveChatConfigured();
            GraphExtractionResult extractionResult = extractionService.extract(runId, documents, chatConfig);
            if (extractionResult.cancelled() || publisher.isCancelled(runId)) {
                long now = System.currentTimeMillis();
                graphRepository.markRunCancelled(runId, now);
                publisher.publishCancelled(runId, Map.of("runId", runId, "status", KnowledgeGraphRunStatus.CANCELLED.name()));
                return;
            }

            publisher.publishProgress(runId, new KnowledgeGraphRunProgress(
                    runId,
                    KnowledgeGraphRunStatus.RUNNING.name(),
                    "MERGING",
                    totalChunks,
                    extractionResult.processedChunkCount(),
                    extractionResult.skippedChunkCount(),
                    extractionResult.failedChunkCount()
            ));
            GraphMergeResult mergeResult = mergeService.merge(scope, runId, documents, chatConfig.id());
            long completedAt = System.currentTimeMillis();
            graphRepository.markRunCompleted(runId, mergeResult.nodeCount(), mergeResult.edgeCount(), completedAt);
            publisher.publishViewReady(runId, KnowledgeGraphViewType.MINDMAP.name());
            publisher.publishViewReady(runId, KnowledgeGraphViewType.GRAPH.name());
            publisher.publishCompleted(runId, Map.of(
                    "runId", runId,
                    "status", KnowledgeGraphRunStatus.COMPLETED.name(),
                    "nodeCount", mergeResult.nodeCount(),
                    "edgeCount", mergeResult.edgeCount()
            ));
        } catch (RuntimeException ex) {
            long now = System.currentTimeMillis();
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            graphRepository.markRunFailed(runId, message, now);
            publisher.publishFailed(runId, Map.of(
                    "runId", runId,
                    "status", KnowledgeGraphRunStatus.FAILED.name(),
                    "message", message
            ));
            log.warn("knowledge_graph_run_failed runId={} scopeType={} scopeId={} reason={}",
                    runId,
                    scope.scopeType(),
                    scope.normalizedScopeId(),
                    message
            );
            log.debug("knowledge_graph_run_failed_stacktrace runId={}", runId, ex);
        }
    }

    /**
     * 按 scope 读取可用于图谱构建的文档快照。
     *
     * @param scope 图谱范围
     * @return 文档索引快照列表
     */
    private List<IndexedDocument> documentsForScope(KnowledgeGraphScope scope) {
        return switch (scope.scopeType()) {
            case ALL -> documentRepository.findAllParsedDocumentsForIndexing();
            case KNOWLEDGE_FOLDER -> documentRepository.findParsedDocumentsForIndexingByKnowledgeFolderId(scope.scopeId());
            case DOCUMENT -> documentRepository.findParsedDocumentForIndexing(scope.scopeId())
                    .map(List::of)
                    .orElseGet(List::of);
        };
    }

    /**
     * 解析并校验图谱范围。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID
     * @return 规范化后的范围
     */
    private KnowledgeGraphScope resolveScope(String scopeType, String scopeId) {
        KnowledgeGraphScopeType type = parseScopeType(scopeType);
        if (type == KnowledgeGraphScopeType.ALL) {
            return new KnowledgeGraphScope(type, null, "全库");
        }
        String normalizedScopeId = scopeId == null ? "" : scopeId.strip();
        if (normalizedScopeId.isBlank()) {
            throw new KnowledgeGraphException(type.name() + " scope requires scopeId");
        }
        if (type == KnowledgeGraphScopeType.KNOWLEDGE_FOLDER) {
            KnowledgeFolder folder = folderRepository.findById(normalizedScopeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Knowledge folder not found: " + normalizedScopeId));
            if (!folder.enabled()) {
                throw new KnowledgeGraphException("Knowledge folder is disabled: " + folder.displayName());
            }
            return new KnowledgeGraphScope(type, normalizedScopeId, folder.displayName());
        }
        KnowledgeDocument document = documentRepository.findById(normalizedScopeId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + normalizedScopeId));
        return new KnowledgeGraphScope(type, normalizedScopeId, document.fileName());
    }

    /**
     * 解析删除请求的图谱范围。
     *
     * <p>删除旧缓存时，原目录或文档可能已经不存在，因此这里只校验 scope 形状，不校验业务对象是否仍存在。</p>
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID
     * @return 可用于数据库删除的范围
     */
    private KnowledgeGraphScope resolveScopeForDeletion(String scopeType, String scopeId) {
        KnowledgeGraphScopeType type = parseScopeType(scopeType);
        if (type == KnowledgeGraphScopeType.ALL) {
            return new KnowledgeGraphScope(type, null, "全库");
        }
        String normalizedScopeId = scopeId == null ? "" : scopeId.strip();
        if (normalizedScopeId.isBlank()) {
            throw new KnowledgeGraphException(type.name() + " scope requires scopeId");
        }
        return new KnowledgeGraphScope(type, normalizedScopeId, normalizedScopeId);
    }

    /**
     * 将 view 聚合行恢复为前端清单项。
     *
     * <p>这里不能复用 resolveScope：历史图谱对应的目录或文档可能已被删除，清单仍应展示兜底入口，
     * 不能因为业务对象缺失导致整个页面加载失败。</p>
     */
    private KnowledgeGraphSummaryResponse toSummaryResponse(KnowledgeGraphSummaryRow row) {
        KnowledgeGraphScopeType scopeType = parseScopeType(row.scopeType());
        String scopeId = row.scopeId();
        ScopeDisplay scopeDisplay = scopeDisplay(scopeType, scopeId);
        KnowledgeGraphScope scope = new KnowledgeGraphScope(
                scopeType,
                scopeType == KnowledgeGraphScopeType.ALL ? null : scopeId,
                scopeDisplay.name()
        );
        return new KnowledgeGraphSummaryResponse(
                scopeType.name(),
                scope.normalizedScopeId(),
                scopeDisplay.name(),
                scopeDisplay.subtitle(),
                graphRepository.countNodesByScope(scope),
                graphRepository.countEdgesByScope(scope),
                row.mindmapReady(),
                row.graphReady(),
                row.generatedAt(),
                graphRepository.findLatestRunForScope(scope).map(KnowledgeGraphRunResponse::from).orElse(null)
        );
    }

    /**
     * 生成清单中用户可读的 scope 标题和副标题。
     *
     * <p>目录和文档删除后保留原始 scopeId 作为副标题，方便用户判断这是哪一份旧图谱缓存。</p>
     */
    private ScopeDisplay scopeDisplay(KnowledgeGraphScopeType scopeType, String scopeId) {
        if (scopeType == KnowledgeGraphScopeType.ALL) {
            return new ScopeDisplay("全库", "全部范围");
        }
        String normalizedScopeId = scopeId == null ? "" : scopeId.strip();
        if (scopeType == KnowledgeGraphScopeType.KNOWLEDGE_FOLDER) {
            return folderRepository.findById(normalizedScopeId)
                    .map(folder -> new ScopeDisplay(folder.displayName(), folder.folderPath()))
                    .orElseGet(() -> new ScopeDisplay("已删除目录", normalizedScopeId));
        }
        return documentRepository.findById(normalizedScopeId)
                .map(document -> new ScopeDisplay(document.fileName(), document.sourcePath()))
                .orElseGet(() -> new ScopeDisplay("已删除文档", normalizedScopeId));
    }

    private record ScopeDisplay(String name, String subtitle) {
    }

    /**
     * 解析图谱范围类型。
     *
     * @param scopeType 请求字符串
     * @return 范围类型
     */
    private static KnowledgeGraphScopeType parseScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            throw new KnowledgeGraphException("scopeType is required");
        }
        try {
            return KnowledgeGraphScopeType.valueOf(scopeType.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new KnowledgeGraphException("Unsupported knowledge graph scopeType: " + scopeType);
        }
    }

    /**
     * 解析图谱视图类型。
     *
     * @param viewType 请求字符串
     * @return 视图类型
     */
    private static KnowledgeGraphViewType parseViewType(String viewType) {
        if (viewType == null || viewType.isBlank()) {
            throw new KnowledgeGraphException("viewType is required");
        }
        try {
            return KnowledgeGraphViewType.valueOf(viewType.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new KnowledgeGraphException("Unsupported knowledge graph viewType: " + viewType);
        }
    }

    /**
     * 取两个视图的最大更新时间。
     *
     * @param left 左视图
     * @param right 右视图
     * @return 最大更新时间；都为空时为 null
     */
    private static Long maxUpdatedAt(KnowledgeGraphView left, KnowledgeGraphView right) {
        Long leftTime = left == null ? null : left.updatedAt();
        Long rightTime = right == null ? null : right.updatedAt();
        if (leftTime == null) {
            return rightTime;
        }
        if (rightTime == null) {
            return leftTime;
        }
        return Math.max(leftTime, rightTime);
    }
}
