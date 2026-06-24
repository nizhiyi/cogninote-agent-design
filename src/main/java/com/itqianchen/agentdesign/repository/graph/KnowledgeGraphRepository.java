package com.itqianchen.agentdesign.repository.graph;

import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphChunkExtraction;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEdge;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEvidence;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphNode;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphRun;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphScope;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphView;
import com.itqianchen.agentdesign.mapper.graph.KnowledgeGraphEvidenceDetailRow;
import com.itqianchen.agentdesign.mapper.graph.KnowledgeGraphMapper;
import com.itqianchen.agentdesign.mapper.graph.KnowledgeGraphSummaryRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 知识图谱仓储。
 * <p>图谱事实和派生视图都存 SQLite；这里集中处理 scope NULL 规则和显式级联删除。</p>
 */
@Repository
public class KnowledgeGraphRepository {

    private final KnowledgeGraphMapper mapper;

    /**
     * 注入知识图谱 Mapper。
     *
     * @param mapper SQLite 图谱访问接口
     */
    public KnowledgeGraphRepository(KnowledgeGraphMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 创建图谱运行记录。
     *
     * @param run 新运行记录
     */
    public void insertRun(KnowledgeGraphRun run) {
        mapper.insertRun(run);
    }

    /**
     * 按 ID 查询图谱运行。
     *
     * @param id 运行 ID
     * @return 运行记录；不存在时为空
     */
    public Optional<KnowledgeGraphRun> findRunById(String id) {
        return mapper.findRunById(id).stream().findFirst();
    }

    /**
     * 查询指定范围内仍在进行的运行。
     *
     * <p>用于避免同一 scope 并发重建；scopeId 会先归一化，保证全局范围使用统一 NULL 语义。</p>
     *
     * @param scope 图谱范围
     * @return 活跃运行；不存在时为空
     */
    public Optional<KnowledgeGraphRun> findActiveRun(KnowledgeGraphScope scope) {
        return mapper.findActiveRun(scope.scopeType().name(), scope.normalizedScopeId()).stream().findFirst();
    }

    /**
     * 查询指定范围最近一次运行。
     *
     * @param scope 图谱范围
     * @return 最近运行；不存在时为空
     */
    public Optional<KnowledgeGraphRun> findLatestRunForScope(KnowledgeGraphScope scope) {
        return mapper.findLatestRunForScope(scope.scopeType().name(), scope.normalizedScopeId()).stream().findFirst();
    }

    /**
     * 查询已生成图谱的 scope 摘要。
     *
     * <p>只读取视图元数据，不解析 payload；前端点击具体条目后再走 view 接口读取完整图谱。</p>
     *
     * @return 已生成图谱摘要行
     */
    public List<KnowledgeGraphSummaryRow> findGeneratedGraphSummaries() {
        return mapper.findGeneratedGraphSummaries();
    }

    /**
     * 标记图谱运行开始。
     *
     * @param id 运行 ID
     * @param totalChunkCount 本轮计划处理的 chunk 总数
     * @param now 当前时间戳
     */
    public void markRunStarted(String id, int totalChunkCount, long now) {
        mapper.markRunStarted(id, totalChunkCount, now, now);
    }

    /**
     * 更新图谱运行进度。
     *
     * @param id 运行 ID
     * @param processedChunkCount 已处理 chunk 数
     * @param skippedChunkCount 因缓存或空结果跳过的 chunk 数
     * @param failedChunkCount 抽取失败的 chunk 数
     * @param now 更新时间戳
     */
    public void updateRunProgress(
            String id,
            int processedChunkCount,
            int skippedChunkCount,
            int failedChunkCount,
            long now
    ) {
        mapper.updateRunProgress(id, processedChunkCount, skippedChunkCount, failedChunkCount, now);
    }

    /**
     * 标记图谱运行成功完成。
     *
     * @param id 运行 ID
     * @param nodeCount 生成节点数量
     * @param edgeCount 生成边数量
     * @param now 完成时间戳
     */
    public void markRunCompleted(String id, int nodeCount, int edgeCount, long now) {
        mapper.markRunCompleted(id, nodeCount, edgeCount, now, now);
    }

    /**
     * 标记图谱运行失败。
     *
     * @param id 运行 ID
     * @param errorMessage 失败原因，供前端展示和排障
     * @param now 失败时间戳
     */
    public void markRunFailed(String id, String errorMessage, long now) {
        mapper.markRunFailed(id, errorMessage, now, now);
    }

    /**
     * 标记图谱运行被取消。
     *
     * @param id 运行 ID
     * @param now 取消时间戳
     */
    public void markRunCancelled(String id, long now) {
        mapper.markRunCancelled(id, now, now);
    }

    /**
     * 将服务重启遗留的运行标记为失败。
     *
     * <p>内存中的执行线程已经不存在，继续显示 RUNNING 会让前端误以为仍可订阅进度。</p>
     *
     * @param errorMessage 标记到运行记录的失败原因
     * @param now 更新时间戳
     */
    public void failOrphanRuns(String errorMessage, long now) {
        mapper.failOrphanRuns(errorMessage, now, now);
    }

    /**
     * 按 chunkId 查询图谱抽取缓存。
     *
     * @param chunkId 文档 chunk ID
     * @return 抽取缓存；不存在时为空
     */
    public Optional<KnowledgeGraphChunkExtraction> findExtractionByChunkId(String chunkId) {
        return mapper.findExtractionByChunkId(chunkId).stream().findFirst();
    }

    /**
     * 批量查询图谱抽取缓存。
     *
     * @param chunkIds 文档 chunk ID 列表；为空时直接返回空列表
     * @return 已存在的抽取缓存
     */
    public List<KnowledgeGraphChunkExtraction> findExtractionsByChunkIds(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        return mapper.findExtractionsByChunkIds(chunkIds);
    }

    /**
     * 新增或更新 chunk 的图谱抽取缓存。
     *
     * <p>缓存按 chunk 内容哈希判断是否可复用，重建图谱时优先避免重复调用模型。</p>
     *
     * @param extraction 抽取结果缓存
     */
    public void upsertChunkExtraction(KnowledgeGraphChunkExtraction extraction) {
        mapper.upsertChunkExtraction(extraction);
    }

    /**
     * 清理已无对应 chunk 的抽取缓存。
     *
     * <p>文档删除或重切块后必须清理，否则缓存会引用不存在的 chunk。</p>
     */
    public void deleteOrphanChunkExtractions() {
        mapper.deleteOrphanChunkExtractions();
    }

    /**
     * 删除知识库目录关联的图谱数据。
     *
     * <p>调用时机在文档和 chunk 删除前，因此还能通过 documents 表定位缓存和派生图。</p>
     *
     * @param knowledgeFolderId 知识库目录 ID
     */
    public void deleteByKnowledgeFolderId(String knowledgeFolderId) {
        /*
         * SQLite foreign_keys 未启用，删除必须从 evidence 开始显式收敛。
         * 目录删除发生在文档/chunk 删除前，因此缓存清理还能通过 documents 表定位。
         */
        deleteScopeDerivedGraph("KNOWLEDGE_FOLDER", knowledgeFolderId);
        mapper.deleteRunsByScope("KNOWLEDGE_FOLDER", knowledgeFolderId);
        mapper.deleteChunkExtractionsByKnowledgeFolderId(knowledgeFolderId);
    }

    /**
     * 删除指定 scope 的派生图谱。
     *
     * <p>只删除节点、边、证据和视图，不删除运行记录或 chunk 抽取缓存。</p>
     *
     * @param scope 图谱范围
     */
    public void deleteScopeDerivedGraph(KnowledgeGraphScope scope) {
        deleteScopeDerivedGraph(scope.scopeType().name(), scope.normalizedScopeId());
    }

    /**
     * 删除用户已生成的某个图谱。
     *
     * <p>运行历史和派生图一起删除，确保清单、状态和证据抽屉不会继续指向旧 scope；chunk 抽取缓存保留，
     * 后续重新生成同一 scope 时仍可复用模型抽取结果。</p>
     *
     * @param scope 图谱范围
     */
    public void deleteGeneratedGraph(KnowledgeGraphScope scope) {
        deleteScopeDerivedGraph(scope);
        mapper.deleteRunsByScope(scope.scopeType().name(), scope.normalizedScopeId());
    }

    /**
     * 按 scope 字段删除派生图谱。
     *
     * <p>删除顺序从 evidence 到 node，兼容未启用 foreign_keys 的 SQLite 连接。</p>
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     */
    private void deleteScopeDerivedGraph(String scopeType, String scopeId) {
        mapper.deleteEvidenceByScope(scopeType, scopeId);
        mapper.deleteViewsByScope(scopeType, scopeId);
        mapper.deleteEdgesByScope(scopeType, scopeId);
        mapper.deleteNodesByScope(scopeType, scopeId);
    }

    /**
     * 插入图谱节点。
     *
     * @param node 已合并去重后的节点
     */
    public void insertNode(KnowledgeGraphNode node) {
        mapper.insertNode(node);
    }

    /**
     * 插入图谱边。
     *
     * @param edge 已合并去重后的边
     */
    public void insertEdge(KnowledgeGraphEdge edge) {
        mapper.insertEdge(edge);
    }

    /**
     * 插入图谱证据。
     *
     * @param evidence 节点或边与原始 chunk 的来源关系
     */
    public void insertEvidence(KnowledgeGraphEvidence evidence) {
        mapper.insertEvidence(evidence);
    }

    /**
     * 插入图谱视图快照。
     *
     * @param view 面向前端的派生视图
     */
    public void insertView(KnowledgeGraphView view) {
        mapper.insertView(view);
    }

    /**
     * 查询指定范围的节点。
     *
     * @param scope 图谱范围
     * @return 节点列表
     */
    public List<KnowledgeGraphNode> findNodesByScope(KnowledgeGraphScope scope) {
        return mapper.findNodesByScope(scope.scopeType().name(), scope.normalizedScopeId());
    }

    /**
     * 查询指定范围的边。
     *
     * @param scope 图谱范围
     * @return 边列表
     */
    public List<KnowledgeGraphEdge> findEdgesByScope(KnowledgeGraphScope scope) {
        return mapper.findEdgesByScope(scope.scopeType().name(), scope.normalizedScopeId());
    }

    /**
     * 查询指定范围和类型的图谱视图。
     *
     * @param scope 图谱范围
     * @param viewType 视图类型
     * @return 视图快照；不存在时为空
     */
    public Optional<KnowledgeGraphView> findView(KnowledgeGraphScope scope, String viewType) {
        return mapper.findView(scope.scopeType().name(), scope.normalizedScopeId(), viewType).stream().findFirst();
    }

    /**
     * 统计指定范围的节点数。
     *
     * @param scope 图谱范围
     * @return 节点数量
     */
    public int countNodesByScope(KnowledgeGraphScope scope) {
        return Math.toIntExact(mapper.countNodesByScope(scope.scopeType().name(), scope.normalizedScopeId()));
    }

    /**
     * 统计指定范围的边数。
     *
     * @param scope 图谱范围
     * @return 边数量
     */
    public int countEdgesByScope(KnowledgeGraphScope scope) {
        return Math.toIntExact(mapper.countEdgesByScope(scope.scopeType().name(), scope.normalizedScopeId()));
    }

    /**
     * 查询节点证据详情。
     *
     * @param nodeId 节点 ID
     * @return 证据详情行
     */
    public List<KnowledgeGraphEvidenceDetailRow> findEvidenceByNodeId(String nodeId) {
        return mapper.findEvidenceByNodeId(nodeId);
    }

    /**
     * 查询边证据详情。
     *
     * @param edgeId 边 ID
     * @return 证据详情行
     */
    public List<KnowledgeGraphEvidenceDetailRow> findEvidenceByEdgeId(String edgeId) {
        return mapper.findEvidenceByEdgeId(edgeId);
    }

    /**
     * 查询指定范围内所有节点证据详情。
     *
     * <p>用于构建图谱视图时批量补来源，避免按节点逐个查询。</p>
     *
     * @param scope 图谱范围
     * @return 节点证据详情行
     */
    public List<KnowledgeGraphEvidenceDetailRow> findNodeEvidenceByScope(KnowledgeGraphScope scope) {
        return mapper.findNodeEvidenceByScope(scope.scopeType().name(), scope.normalizedScopeId());
    }
}
