package com.itqianchen.agentdesign.service.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphChunkExtraction;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEdge;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEvidence;
import com.itqianchen.agentdesign.domain.enums.graph.KnowledgeGraphExtractionStatus;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphNode;
import com.itqianchen.agentdesign.domain.properties.graph.KnowledgeGraphPromptProperties;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphScope;
import com.itqianchen.agentdesign.domain.vo.search.IndexedChunk;
import com.itqianchen.agentdesign.domain.vo.search.IndexedDocument;
import com.itqianchen.agentdesign.repository.graph.KnowledgeGraphRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图谱派生层 merge 服务。
 * <p>merge 只使用本地缓存和 SQLite 短事务，不再调用模型。</p>
 */
@Service
public class GraphMergeService {

    private static final Logger log = LoggerFactory.getLogger(GraphMergeService.class);
    private static final int MAX_DESCRIPTION_LENGTH = 280;

    private final KnowledgeGraphRepository repository;
    private final GraphCanonicalizer canonicalizer;
    private final GraphViewBuilder viewBuilder;
    private final ObjectMapper objectMapper;
    private final KnowledgeGraphPromptProperties promptProperties;

    /**
     * 注入图谱合并服务依赖。
     *
     * @param repository 图谱仓储
     * @param canonicalizer 图谱规范化工具
     * @param viewBuilder 视图构建器
     * @param objectMapper JSON 解析器
     * @param promptProperties 图谱提示词配置
     */
    public GraphMergeService(
            KnowledgeGraphRepository repository,
            GraphCanonicalizer canonicalizer,
            GraphViewBuilder viewBuilder,
            ObjectMapper objectMapper,
            KnowledgeGraphPromptProperties promptProperties
    ) {
        this.repository = repository;
        this.canonicalizer = canonicalizer;
        this.viewBuilder = viewBuilder;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
    }

    /**
     * 将 chunk 抽取缓存合并为 scope 派生图。
     *
     * <p>合并阶段不调用模型，只从已验证 quote 的缓存中生成节点、边、证据和前端视图。</p>
     *
     * @param scope 图谱范围
     * @param runId 运行 ID
     * @param documents 本次参与合并的文档
     * @param modelConfigId 抽取时使用的模型配置 ID
     * @return 合并统计
     */
    @Transactional
    public GraphMergeResult merge(
            KnowledgeGraphScope scope,
            String runId,
            List<IndexedDocument> documents,
            String modelConfigId
    ) {
        Map<String, IndexedChunk> chunksById = chunksById(documents);
        Map<NodeKey, NodeAccumulator> nodes = new LinkedHashMap<>();
        Map<EdgeKey, EdgeAccumulator> edges = new LinkedHashMap<>();
        long now = System.currentTimeMillis();

        // 派生图可以从 chunk 抽取缓存重建，先清理旧节点/边/证据可避免跨 run 残留。
        repository.deleteScopeDerivedGraph(scope);
        for (KnowledgeGraphChunkExtraction extraction : repository.findExtractionsByChunkIds(new ArrayList<>(chunksById.keySet()))) {
            IndexedChunk chunk = chunksById.get(extraction.chunkId());
            if (!isReusableExtraction(extraction, chunk, modelConfigId)) {
                continue;
            }
            mergeExtraction(scope, runId, chunk, extraction, nodes, edges, now);
        }

        List<NodeAccumulator> persistedNodes = nodes.values().stream()
                .filter(node -> node.mentionCount > 0)
                .toList();
        Set<String> persistedNodeIds = new HashSet<>(persistedNodes.stream().map(NodeAccumulator::id).toList());
        List<EdgeAccumulator> persistedEdges = edges.values().stream()
                .filter(edge -> edge.mentionCount > 0)
                .filter(edge -> persistedNodeIds.contains(edge.sourceNodeId) && persistedNodeIds.contains(edge.targetNodeId))
                .toList();

        for (NodeAccumulator node : persistedNodes) {
            repository.insertNode(node.toNode(scope, now));
        }
        for (EdgeAccumulator edge : persistedEdges) {
            repository.insertEdge(edge.toEdge(scope, now));
        }
        for (NodeAccumulator node : persistedNodes) {
            for (KnowledgeGraphEvidence evidence : node.evidence) {
                repository.insertEvidence(evidence);
            }
        }
        for (EdgeAccumulator edge : persistedEdges) {
            for (KnowledgeGraphEvidence evidence : edge.evidence) {
                repository.insertEvidence(evidence);
            }
        }

        viewBuilder.buildViews(scope, runId, documents);
        log.info("knowledge_graph_merged runId={} scopeType={} scopeId={} nodes={} edges={}",
                runId,
                scope.scopeType(),
                scope.normalizedScopeId(),
                persistedNodes.size(),
                persistedEdges.size()
        );
        return new GraphMergeResult(persistedNodes.size(), persistedEdges.size());
    }

    /**
     * 合并单个 chunk 的抽取结果。
     *
     * @param scope 图谱范围
     * @param runId 运行 ID
     * @param chunk 当前 chunk
     * @param extraction 抽取缓存
     * @param nodes 节点累加器
     * @param edges 边累加器
     * @param now 当前时间戳
     */
    private void mergeExtraction(
            KnowledgeGraphScope scope,
            String runId,
            IndexedChunk chunk,
            KnowledgeGraphChunkExtraction extraction,
            Map<NodeKey, NodeAccumulator> nodes,
            Map<EdgeKey, EdgeAccumulator> edges,
            long now
    ) {
        GraphExtractionPayload payload = parse(extraction.extractionJson());
        Map<String, NodeKey> localNameToNodeKey = new HashMap<>();
        for (GraphExtractionPayload.Node extractedNode : nullToEmpty(payload.nodes())) {
            String canonicalName = canonicalizer.canonicalName(extractedNode.name());
            if (canonicalName.isBlank()) {
                continue;
            }
            String nodeType = canonicalizer.nodeType(extractedNode.type());
            NodeKey key = new NodeKey(canonicalName, nodeType);
            NodeAccumulator node = nodes.computeIfAbsent(
                    key,
                    ignored -> new NodeAccumulator(scope, key, extractedNode.name(), nodeType)
            );
            localNameToNodeKey.putIfAbsent(canonicalName, key);
            if (canonicalizer.quoteMatches(chunk.content(), extractedNode.quote())) {
                // 只接受能回链到原文 quote 的证据，防止模型幻觉实体进入可追溯视图。
                node.addEvidence(
                        runId,
                        chunk,
                        extractedNode.description(),
                        normalizeConfidence(extractedNode.confidence()),
                        extractedNode.quote(),
                        false,
                        now
                );
            }
        }

        for (GraphExtractionPayload.Edge extractedEdge : nullToEmpty(payload.edges())) {
            NodeKey sourceKey = localNameToNodeKey.get(canonicalizer.canonicalName(extractedEdge.source()));
            NodeKey targetKey = localNameToNodeKey.get(canonicalizer.canonicalName(extractedEdge.target()));
            if (sourceKey == null || targetKey == null || Objects.equals(sourceKey, targetKey)) {
                continue;
            }
            if (!canonicalizer.quoteMatches(chunk.content(), extractedEdge.quote())) {
                // 关系必须有原文证据；没有可验证 quote 时宁可丢弃。
                continue;
            }

            NodeAccumulator source = nodes.get(sourceKey);
            NodeAccumulator target = nodes.get(targetKey);
            if (source == null || target == null) {
                continue;
            }
            double confidence = normalizeConfidence(extractedEdge.confidence());
            source.addEvidence(runId, chunk, null, confidence, extractedEdge.quote(), true, now);
            target.addEvidence(runId, chunk, null, confidence, extractedEdge.quote(), true, now);

            String relationType = canonicalizer.relationType(extractedEdge.type());
            String displayLabel = canonicalizer.relationDisplayLabel(extractedEdge.displayLabel());
            // 旧 chunk 缓存可能绕过 v2 抽取清洗，merge 阶段再兜底一次，避免事实表写回英文描述。
            String description = canonicalizer.relationDescription(
                    source.displayName,
                    target.displayName,
                    displayLabel,
                    extractedEdge.description(),
                    MAX_DESCRIPTION_LENGTH
            );
            // 同一粗分类下的“使用/依赖/通知”语义不同，displayLabel 必须参与边去重。
            EdgeKey edgeKey = new EdgeKey(source.id(), target.id(), relationType, displayLabel);
            EdgeAccumulator edge = edges.computeIfAbsent(
                    edgeKey,
                    ignored -> new EdgeAccumulator(edgeKey, relationType, displayLabel)
            );
            edge.addEvidence(runId, chunk, description, confidence, extractedEdge.quote(), now);
        }
    }

    /**
     * 解析缓存中的抽取 JSON。
     *
     * @param json 抽取 JSON
     * @return 抽取 payload
     */
    private GraphExtractionPayload parse(String json) {
        try {
            return objectMapper.readValue(json, GraphExtractionPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("cached graph extraction json is invalid", ex);
        }
    }

    /**
     * 判断抽取缓存是否可用于本次合并。
     *
     * @param extraction 抽取缓存
     * @param chunk 当前 chunk
     * @param modelConfigId 当前模型配置 ID
     * @return 是否可复用
     */
    private boolean isReusableExtraction(KnowledgeGraphChunkExtraction extraction, IndexedChunk chunk, String modelConfigId) {
        return chunk != null
                && extraction.status() == KnowledgeGraphExtractionStatus.EXTRACTED
                && Objects.equals(extraction.contentHash(), chunk.contentHash())
                && Objects.equals(extraction.promptVersion(), promptProperties.extraction().version())
                && Objects.equals(extraction.modelConfigId(), modelConfigId)
                && extraction.extractionJson() != null
                && !extraction.extractionJson().isBlank();
    }

    /**
     * 将文档集合索引为 chunkId 到 chunk。
     *
     * @param documents 文档集合
     * @return chunk 映射
     */
    private static Map<String, IndexedChunk> chunksById(Collection<IndexedDocument> documents) {
        Map<String, IndexedChunk> chunks = new LinkedHashMap<>();
        for (IndexedDocument document : documents) {
            for (IndexedChunk chunk : document.chunks()) {
                chunks.put(chunk.id(), chunk);
            }
        }
        return chunks;
    }

    /**
     * 归一化置信度。
     *
     * @param confidence 模型输出置信度
     * @return 0 到 1 之间的置信度
     */
    private static double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return 0.0;
        }
        return Math.clamp(confidence, 0.0, 1.0);
    }

    /**
     * 将可空列表转换为空列表。
     *
     * @param values 可空列表
     * @return 非空列表
     */
    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record NodeKey(String canonicalName, String nodeType) {
    }

    private record EdgeKey(String sourceNodeId, String targetNodeId, String relationType, String displayLabel) {
    }

    private class NodeAccumulator {
        private final NodeKey key;
        private final String id;
        private final String displayName;
        private final String nodeType;
        private final List<KnowledgeGraphEvidence> evidence = new ArrayList<>();
        private final Set<String> evidenceIds = new HashSet<>();
        private String description;
        private double confidenceTotal;
        private int mentionCount;

        /**
         * 创建节点累加器。
         *
         * @param scope 图谱范围
         * @param key 节点合并键
         * @param displayName 展示名称
         * @param nodeType 节点类型
         */
        private NodeAccumulator(KnowledgeGraphScope scope, NodeKey key, String displayName, String nodeType) {
            this.key = key;
            this.id = canonicalizer.stableId(scopeSeed(scope) + "|node|" + key.canonicalName() + "|" + key.nodeType());
            this.displayName = canonicalizer.displayText(displayName, 120);
            this.nodeType = nodeType;
        }

        /**
         * 返回节点 ID。
         *
         * @return 节点 ID
         */
        private String id() {
            return id;
        }

        /**
         * 为节点追加证据。
         *
         * @param runId 运行 ID
         * @param chunk 来源 chunk
         * @param description 节点描述
         * @param confidence 置信度
         * @param quote 原文证据
         * @param fromEdge 是否来自边证据补充
         * @param now 当前时间戳
         */
        private void addEvidence(
                String runId,
                IndexedChunk chunk,
                String description,
                double confidence,
                String quote,
                boolean fromEdge,
                long now
        ) {
            String normalizedQuote = canonicalizer.displayText(quote, 260);
            if (normalizedQuote.isBlank()) {
                return;
            }
            String evidenceId = canonicalizer.stableId(runId + "|node|" + id + "|" + chunk.id() + "|" + normalizedQuote);
            if (!evidenceIds.add(evidenceId)) {
                return;
            }
            mentionCount++;
            confidenceTotal += confidence;
            if (!fromEdge && this.description == null && description != null && !description.isBlank()) {
                this.description = canonicalizer.displayText(description, 280);
            }
            evidence.add(new KnowledgeGraphEvidence(
                    evidenceId,
                    runId,
                    id,
                    null,
                    chunk.documentId(),
                    chunk.id(),
                    normalizedQuote,
                    confidence,
                    now
            ));
        }

        /**
         * 转换为可持久化节点。
         *
         * @param scope 图谱范围
         * @param now 当前时间戳
         * @return 图谱节点
         */
        private KnowledgeGraphNode toNode(KnowledgeGraphScope scope, long now) {
            return new KnowledgeGraphNode(
                    id,
                    scope.scopeType(),
                    scope.normalizedScopeId(),
                    key.canonicalName(),
                    displayName,
                    nodeType,
                    description,
                    mentionCount == 0 ? 0 : confidenceTotal / mentionCount,
                    mentionCount,
                    now,
                    now
            );
        }
    }

    private class EdgeAccumulator {
        private final EdgeKey key;
        private final String id;
        private final String sourceNodeId;
        private final String targetNodeId;
        private final String relationType;
        private final String displayLabel;
        private final List<KnowledgeGraphEvidence> evidence = new ArrayList<>();
        private final Set<String> evidenceIds = new HashSet<>();
        private String description;
        private double confidenceTotal;
        private int mentionCount;

        /**
         * 创建边累加器。
         *
         * @param key 边合并键
         * @param relationType 关系类型
         * @param displayLabel 中文展示谓词
         */
        private EdgeAccumulator(EdgeKey key, String relationType, String displayLabel) {
            this.key = key;
            this.id = canonicalizer.stableId("edge|" + key.sourceNodeId() + "|" + key.targetNodeId()
                    + "|" + key.relationType() + "|" + key.displayLabel());
            this.sourceNodeId = key.sourceNodeId();
            this.targetNodeId = key.targetNodeId();
            this.relationType = relationType;
            this.displayLabel = displayLabel;
        }

        /**
         * 为边追加证据。
         *
         * @param runId 运行 ID
         * @param chunk 来源 chunk
         * @param description 关系描述
         * @param confidence 置信度
         * @param quote 原文证据
         * @param now 当前时间戳
         */
        private void addEvidence(
                String runId,
                IndexedChunk chunk,
                String description,
                double confidence,
                String quote,
                long now
        ) {
            String normalizedQuote = canonicalizer.displayText(quote, 260);
            if (normalizedQuote.isBlank()) {
                return;
            }
            String evidenceId = canonicalizer.stableId(runId + "|edge|" + id + "|" + chunk.id() + "|" + normalizedQuote);
            if (!evidenceIds.add(evidenceId)) {
                return;
            }
            mentionCount++;
            confidenceTotal += confidence;
            if (this.description == null && description != null && !description.isBlank()) {
                this.description = canonicalizer.displayText(description, 280);
            }
            evidence.add(new KnowledgeGraphEvidence(
                    evidenceId,
                    runId,
                    null,
                    id,
                    chunk.documentId(),
                    chunk.id(),
                    normalizedQuote,
                    confidence,
                    now
            ));
        }

        /**
         * 转换为可持久化边。
         *
         * @param scope 图谱范围
         * @param now 当前时间戳
         * @return 图谱边
         */
        private KnowledgeGraphEdge toEdge(KnowledgeGraphScope scope, long now) {
            return new KnowledgeGraphEdge(
                    id,
                    scope.scopeType(),
                    scope.normalizedScopeId(),
                    sourceNodeId,
                    targetNodeId,
                    relationType,
                    displayLabel,
                    description,
                    mentionCount == 0 ? 0 : confidenceTotal / mentionCount,
                    mentionCount,
                    now,
                    now
            );
        }
    }

    /**
     * 构造 scope 稳定 ID 种子。
     *
     * @param scope 图谱范围
     * @return scope seed
     */
    private static String scopeSeed(KnowledgeGraphScope scope) {
        return scope.scopeType().name() + "|" + (scope.normalizedScopeId() == null ? "" : scope.normalizedScopeId());
    }
}
