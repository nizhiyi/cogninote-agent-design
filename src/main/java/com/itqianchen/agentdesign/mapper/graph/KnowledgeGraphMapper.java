package com.itqianchen.agentdesign.mapper.graph;

import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphChunkExtraction;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEdge;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphEvidence;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphNode;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphRun;
import com.itqianchen.agentdesign.domain.entity.graph.KnowledgeGraphView;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 知识图谱 MyBatis Mapper。
 * <p>所有图谱数据仍落在 SQLite；外键级联不可用，因此删除操作必须显式声明。</p>
 */
public interface KnowledgeGraphMapper {

    /**
     * 插入图谱运行记录。
     *
     * @param run 运行记录
     */
    void insertRun(KnowledgeGraphRun run);

    /**
     * 按 ID 查询运行记录。
     *
     * @param id 运行 ID
     * @return 运行记录
     */
    List<KnowledgeGraphRun> findRunById(@Param("id") String id);

    /**
     * 查询指定 scope 中仍未结束的运行。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 活跃运行记录
     */
    List<KnowledgeGraphRun> findActiveRun(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 查询指定 scope 最近一次运行。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 最近运行记录
     */
    List<KnowledgeGraphRun> findLatestRunForScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 查询已有图谱视图的 scope 摘要。
     *
     * @return 已生成图谱的轻量 scope 列表
     */
    List<KnowledgeGraphSummaryRow> findGeneratedGraphSummaries();

    /**
     * 标记运行开始并写入计划 chunk 总数。
     *
     * @param id 运行 ID
     * @param totalChunkCount 计划处理的 chunk 总数
     * @param startedAt 开始时间戳
     * @param updatedAt 更新时间戳
     */
    void markRunStarted(
            @Param("id") String id,
            @Param("totalChunkCount") int totalChunkCount,
            @Param("startedAt") long startedAt,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 更新运行进度计数。
     *
     * @param id 运行 ID
     * @param processedChunkCount 已处理 chunk 数
     * @param skippedChunkCount 跳过 chunk 数
     * @param failedChunkCount 失败 chunk 数
     * @param updatedAt 更新时间戳
     */
    void updateRunProgress(
            @Param("id") String id,
            @Param("processedChunkCount") int processedChunkCount,
            @Param("skippedChunkCount") int skippedChunkCount,
            @Param("failedChunkCount") int failedChunkCount,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 标记运行完成。
     *
     * @param id 运行 ID
     * @param extractedNodeCount 最终节点数量
     * @param extractedEdgeCount 最终边数量
     * @param completedAt 完成时间戳
     * @param updatedAt 更新时间戳
     */
    void markRunCompleted(
            @Param("id") String id,
            @Param("extractedNodeCount") int extractedNodeCount,
            @Param("extractedEdgeCount") int extractedEdgeCount,
            @Param("completedAt") long completedAt,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 标记运行失败。
     *
     * @param id 运行 ID
     * @param errorMessage 失败原因
     * @param completedAt 结束时间戳
     * @param updatedAt 更新时间戳
     */
    void markRunFailed(
            @Param("id") String id,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") long completedAt,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 标记运行取消。
     *
     * @param id 运行 ID
     * @param completedAt 结束时间戳
     * @param updatedAt 更新时间戳
     */
    void markRunCancelled(
            @Param("id") String id,
            @Param("completedAt") long completedAt,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 将重启后无人接管的运行标记为失败。
     *
     * @param errorMessage 失败原因
     * @param completedAt 结束时间戳
     * @param updatedAt 更新时间戳
     */
    void failOrphanRuns(
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") long completedAt,
            @Param("updatedAt") long updatedAt
    );

    /**
     * 查询单个 chunk 的抽取缓存。
     *
     * @param chunkId 文档 chunk ID
     * @return 抽取缓存
     */
    List<KnowledgeGraphChunkExtraction> findExtractionByChunkId(@Param("chunkId") String chunkId);

    /**
     * 批量查询 chunk 抽取缓存。
     *
     * @param chunkIds chunk ID 列表
     * @return 抽取缓存列表
     */
    List<KnowledgeGraphChunkExtraction> findExtractionsByChunkIds(@Param("chunkIds") List<String> chunkIds);

    /**
     * 新增或更新 chunk 抽取缓存。
     *
     * @param extraction 抽取缓存
     */
    void upsertChunkExtraction(KnowledgeGraphChunkExtraction extraction);

    /**
     * 删除已失去 chunk 来源的抽取缓存。
     */
    void deleteOrphanChunkExtractions();

    /**
     * 删除指定知识库目录下的 chunk 抽取缓存。
     *
     * @param knowledgeFolderId 知识库目录 ID
     */
    void deleteChunkExtractionsByKnowledgeFolderId(@Param("knowledgeFolderId") String knowledgeFolderId);

    /**
     * 删除指定 scope 的证据记录。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     */
    void deleteEvidenceByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 删除指定 scope 的前端视图快照。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     */
    void deleteViewsByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 删除指定 scope 的边。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     */
    void deleteEdgesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 删除指定 scope 的节点。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     */
    void deleteNodesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 删除指定 scope 的运行记录。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     */
    void deleteRunsByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 插入节点。
     *
     * @param node 图谱节点
     */
    void insertNode(KnowledgeGraphNode node);

    /**
     * 插入边。
     *
     * @param edge 图谱边
     */
    void insertEdge(KnowledgeGraphEdge edge);

    /**
     * 插入来源证据。
     *
     * @param evidence 图谱证据
     */
    void insertEvidence(KnowledgeGraphEvidence evidence);

    /**
     * 插入前端视图快照。
     *
     * @param view 图谱视图
     */
    void insertView(KnowledgeGraphView view);

    /**
     * 查询指定 scope 的节点。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 节点列表
     */
    List<KnowledgeGraphNode> findNodesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 查询指定 scope 的边。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 边列表
     */
    List<KnowledgeGraphEdge> findEdgesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 查询指定 scope 和类型的视图快照。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @param viewType 视图类型
     * @return 图谱视图记录
     */
    List<KnowledgeGraphView> findView(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("viewType") String viewType
    );

    /**
     * 统计指定 scope 的节点数量。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 节点数量
     */
    long countNodesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 统计指定 scope 的边数量。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 边数量
     */
    long countEdgesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );

    /**
     * 查询节点的证据详情。
     *
     * @param nodeId 节点 ID
     * @return 证据详情行
     */
    List<KnowledgeGraphEvidenceDetailRow> findEvidenceByNodeId(@Param("nodeId") String nodeId);

    /**
     * 查询边的证据详情。
     *
     * @param edgeId 边 ID
     * @return 证据详情行
     */
    List<KnowledgeGraphEvidenceDetailRow> findEvidenceByEdgeId(@Param("edgeId") String edgeId);

    /**
     * 查询指定 scope 下节点证据详情。
     *
     * @param scopeType 范围类型
     * @param scopeId 归一化范围 ID；全局范围为 null
     * @return 证据详情行
     */
    List<KnowledgeGraphEvidenceDetailRow> findNodeEvidenceByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );
}
