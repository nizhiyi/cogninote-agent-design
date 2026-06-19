package com.itqianchen.agentdesign.service.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphChunkExtraction;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphExtractionStatus;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphPromptProperties;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.search.IndexedChunk;
import com.itqianchen.agentdesign.domain.search.IndexedDocument;
import com.itqianchen.agentdesign.repository.graph.KnowledgeGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 单 chunk 模型抽取服务。
 * <p>模型调用是唯一昂贵步骤，因此缓存命中只跳过模型调用，merge 仍会从缓存全量重跑。</p>
 */
@Service
public class GraphExtractionService {

    private static final Logger log = LoggerFactory.getLogger(GraphExtractionService.class);
    private static final int MAX_NODES_PER_CHUNK = 24;
    private static final int MAX_EDGES_PER_CHUNK = 32;
    private static final int MAX_DESCRIPTION_LENGTH = 280;
    private static final int MAX_QUOTE_LENGTH = 260;
    private static final int DB_PROGRESS_FLUSH_CHUNKS = 10;
    private static final long DB_PROGRESS_FLUSH_MILLIS = 5_000L;

    private final KnowledgeGraphRepository repository;
    private final KnowledgeGraphRunPublisher publisher;
    private final AiRuntimeFactory aiRuntimeFactory;
    private final GraphCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final KnowledgeGraphPromptProperties promptProperties;

    /**
     * 注入图谱抽取服务依赖。
     *
     * @param repository 图谱仓储
     * @param publisher 运行事件发布器
     * @param aiRuntimeFactory AI 运行时工厂
     * @param canonicalizer 图谱规范化工具
     * @param objectMapper JSON 编解码器
     * @param promptProperties 图谱提示词配置
     */
    public GraphExtractionService(
            KnowledgeGraphRepository repository,
            KnowledgeGraphRunPublisher publisher,
            AiRuntimeFactory aiRuntimeFactory,
            GraphCanonicalizer canonicalizer,
            ObjectMapper objectMapper,
            KnowledgeGraphPromptProperties promptProperties
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
    }

    /**
     * 抽取一批文档 chunk 的图谱缓存。
     *
     * <p>该阶段只生成或复用 chunk 级缓存，不直接写节点边；merge 阶段会基于缓存重建派生图。</p>
     *
     * @param runId 运行 ID
     * @param documents 待抽取文档
     * @param chatConfig Chat 模型配置快照
     * @return 抽取统计
     */
    public GraphExtractionResult extract(
            String runId,
            List<IndexedDocument> documents,
            ModelConfig chatConfig
    ) {
        List<ChunkWorkItem> chunks = flatten(documents);
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        int sinceLastFlush = 0;
        long lastFlushAt = System.currentTimeMillis();

        for (ChunkWorkItem item : chunks) {
            if (publisher.isCancelled(runId)) {
                persistProgress(runId, processed, skipped, failed);
                return new GraphExtractionResult(processed, skipped, failed, true);
            }

            try {
                // 缓存必须同时匹配内容哈希、Prompt 版本和模型配置，任一变化都要重新抽取。
                if (isCacheHit(item.chunk(), chatConfig.id())) {
                    skipped++;
                } else {
                    extractOne(runId, item, chatConfig);
                }
            } catch (RuntimeException ex) {
                failed++;
                log.warn("knowledge_graph_chunk_extract_failed runId={} chunkId={} reason={}",
                        runId,
                        item.chunk().id(),
                        ex.getMessage()
                );
                log.debug("knowledge_graph_chunk_extract_failed_stacktrace runId={} chunkId={}",
                        runId,
                        item.chunk().id(),
                        ex
                );
                saveFailedExtraction(item.chunk(), ex.getMessage(), chatConfig.id());
            }

            processed++;
            sinceLastFlush++;
            KnowledgeGraphRunProgress progress = new KnowledgeGraphRunProgress(
                    runId,
                    "RUNNING",
                    "EXTRACTING",
                    chunks.size(),
                    processed,
                    skipped,
                    failed
            );
            publisher.publishProgress(runId, progress);

            long now = System.currentTimeMillis();
            if (sinceLastFlush >= DB_PROGRESS_FLUSH_CHUNKS || now - lastFlushAt >= DB_PROGRESS_FLUSH_MILLIS) {
                // SSE 负责高频前端反馈；数据库只周期性落进度，避免每个 chunk 都写 SQLite。
                repository.updateRunProgress(runId, processed, skipped, failed, now);
                sinceLastFlush = 0;
                lastFlushAt = now;
            }
        }

        persistProgress(runId, processed, skipped, failed);
        return new GraphExtractionResult(processed, skipped, failed, false);
    }

    /**
     * 统计文档集合中的 chunk 数。
     *
     * @param documents 文档索引快照
     * @return chunk 数量
     */
    public int countChunks(List<IndexedDocument> documents) {
        return flatten(documents).size();
    }

    /**
     * 返回当前图谱抽取 Prompt 版本。
     *
     * @return Prompt 版本
     */
    public String promptVersion() {
        return promptProperties.extraction().version();
    }

    /**
     * 对单个 chunk 调用模型并保存抽取结果。
     *
     * @param runId 运行 ID
     * @param item chunk 工作项
     * @param chatConfig Chat 模型配置
     */
    private void extractOne(String runId, ChunkWorkItem item, ModelConfig chatConfig) {
        String response = aiRuntimeFactory.chatRuntime(chatConfig)
                .callText(promptProperties.extraction().system(), userPrompt(item));
        String json = extractJson(response);
        GraphExtractionPayload payload = parsePayload(json);
        GraphExtractionPayload sanitized = sanitize(payload);
        try {
            String sanitizedJson = objectMapper.writeValueAsString(sanitized);
            long now = System.currentTimeMillis();
            repository.upsertChunkExtraction(new KnowledgeGraphChunkExtraction(
                    item.chunk().id(),
                    item.chunk().documentId(),
                    item.chunk().contentHash(),
                    promptVersion(),
                    chatConfig.id(),
                    KnowledgeGraphExtractionStatus.EXTRACTED,
                    sanitizedJson,
                    null,
                    now
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize graph extraction", ex);
        }
        log.debug("knowledge_graph_chunk_extracted runId={} chunkId={}", runId, item.chunk().id());
    }

    /**
     * 判断 chunk 抽取缓存是否仍可复用。
     *
     * @param chunk 当前 chunk
     * @param modelConfigId 当前模型配置 ID
     * @return 是否命中缓存
     */
    private boolean isCacheHit(IndexedChunk chunk, String modelConfigId) {
        return repository.findExtractionByChunkId(chunk.id())
                .filter(extraction -> extraction.status() == KnowledgeGraphExtractionStatus.EXTRACTED)
                .filter(extraction -> Objects.equals(extraction.contentHash(), chunk.contentHash()))
                .filter(extraction -> Objects.equals(extraction.promptVersion(), promptVersion()))
                .filter(extraction -> Objects.equals(extraction.modelConfigId(), modelConfigId))
                .isPresent();
    }

    /**
     * 保存单 chunk 抽取失败状态。
     *
     * @param chunk 失败 chunk
     * @param message 失败消息
     * @param modelConfigId 模型配置 ID
     */
    private void saveFailedExtraction(IndexedChunk chunk, String message, String modelConfigId) {
        long now = System.currentTimeMillis();
        repository.upsertChunkExtraction(new KnowledgeGraphChunkExtraction(
                chunk.id(),
                chunk.documentId(),
                chunk.contentHash(),
                promptVersion(),
                modelConfigId,
                KnowledgeGraphExtractionStatus.FAILED,
                null,
                canonicalizer.displayText(message, 600),
                now
        ));
    }

    /**
     * 解析模型返回的图谱抽取 JSON。
     *
     * @param json JSON 字符串
     * @return 抽取 payload
     */
    private GraphExtractionPayload parsePayload(String json) {
        try {
            return objectMapper.readValue(json, GraphExtractionPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid graph extraction json", ex);
        }
    }

    /**
     * 清洗模型抽取结果。
     *
     * <p>节点、边数量和字段长度都在这里收口，避免模型输出异常放大到数据库和前端视图。</p>
     *
     * @param payload 模型抽取结果
     * @return 清洗后的 payload
     */
    private GraphExtractionPayload sanitize(GraphExtractionPayload payload) {
        List<GraphExtractionPayload.Node> nodes = new ArrayList<>();
        for (GraphExtractionPayload.Node node : nullToEmpty(payload.nodes())) {
            if (nodes.size() >= MAX_NODES_PER_CHUNK) {
                break;
            }
            // 模型输出不可信，入库前统一裁剪长度和枚举值，避免污染图谱视图 JSON。
            String name = canonicalizer.displayText(node.name(), 120);
            if (name.isBlank()) {
                continue;
            }
            nodes.add(new GraphExtractionPayload.Node(
                    name,
                    canonicalizer.nodeType(node.type()),
                    canonicalizer.displayText(node.description(), MAX_DESCRIPTION_LENGTH),
                    normalizeConfidence(node.confidence()),
                    canonicalizer.displayText(node.quote(), MAX_QUOTE_LENGTH)
            ));
        }

        List<GraphExtractionPayload.Edge> edges = new ArrayList<>();
        for (GraphExtractionPayload.Edge edge : nullToEmpty(payload.edges())) {
            if (edges.size() >= MAX_EDGES_PER_CHUNK) {
                break;
            }
            String source = canonicalizer.displayText(edge.source(), 120);
            String target = canonicalizer.displayText(edge.target(), 120);
            if (source.isBlank() || target.isBlank()) {
                continue;
            }
            String displayLabel = canonicalizer.relationDisplayLabel(edge.displayLabel());
            // v2 缓存中就写入已校验的中文关系语义，避免后续 merge/view 再处理原始模型噪音。
            edges.add(new GraphExtractionPayload.Edge(
                    source,
                    target,
                    canonicalizer.relationType(edge.type()),
                    displayLabel,
                    canonicalizer.relationDescription(
                            source,
                            target,
                            displayLabel,
                            edge.description(),
                            MAX_DESCRIPTION_LENGTH
                    ),
                    normalizeConfidence(edge.confidence()),
                    canonicalizer.displayText(edge.quote(), MAX_QUOTE_LENGTH)
            ));
        }
        return new GraphExtractionPayload(nodes, edges);
    }

    /**
     * 持久化运行进度。
     *
     * @param runId 运行 ID
     * @param processed 已处理 chunk 数
     * @param skipped 跳过 chunk 数
     * @param failed 失败 chunk 数
     */
    private void persistProgress(String runId, int processed, int skipped, int failed) {
        repository.updateRunProgress(runId, processed, skipped, failed, System.currentTimeMillis());
    }

    /**
     * 将文档集合展开为 chunk 工作项。
     *
     * @param documents 文档索引快照
     * @return chunk 工作项列表
     */
    private static List<ChunkWorkItem> flatten(List<IndexedDocument> documents) {
        List<ChunkWorkItem> chunks = new ArrayList<>();
        for (IndexedDocument document : documents) {
            for (IndexedChunk chunk : document.chunks()) {
                chunks.add(new ChunkWorkItem(document, chunk));
            }
        }
        return chunks;
    }

    /**
     * 从模型响应中截取 JSON 对象。
     *
     * @param response 模型响应
     * @return JSON 对象字符串
     */
    private static String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("graph extraction response is blank");
        }
        String stripped = response.strip();
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("graph extraction response does not contain json object");
        }
        return stripped.substring(start, end + 1);
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

    /**
     * 构造单 chunk 抽取用户提示词。
     *
     * @param item chunk 工作项
     * @return 用户提示词
     */
    private String userPrompt(ChunkWorkItem item) {
        IndexedDocument document = item.document();
        IndexedChunk chunk = item.chunk();
        return promptProperties.extraction().user()
                .replace("{documentName}", fallbackText(document.fileName()))
                .replace("{chunkId}", fallbackText(chunk.id()))
                .replace("{heading}", fallbackText(chunk.heading()))
                .replace("{pageNumber}", chunk.pageNumber() == null ? "无" : chunk.pageNumber().toString())
                .replace("{content}", fallbackText(chunk.content()));
    }

    /**
     * 将空文本替换为“无”。
     *
     * @param value 原始文本
     * @return 非空展示文本
     */
    private static String fallbackText(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private record ChunkWorkItem(IndexedDocument document, IndexedChunk chunk) {
    }
}
