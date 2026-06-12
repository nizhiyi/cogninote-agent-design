package com.itqianchen.agentdesign.mapper.schema;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 启动期数据库 schema 初始化专用 Mapper。
 *
 * <p>该接口包含 DDL 和迁移 SQL，只能由 DatabaseSchemaInitializer 使用。动态表名、列名和字段定义
 * 必须来自内部白名单常量，不能暴露给外部请求。</p>
 */
public interface DatabaseSchemaMapper {

    /**
     * 创建知识库目录表。
     */
    void createKnowledgeFoldersTable();

    /**
     * 创建文档元数据表。
     */
    void createDocumentsTable();

    /**
     * 创建文档 chunk 表。
     */
    void createChunksTable();

    /**
     * 创建旧版模型配置表，用于兼容迁移。
     */
    void createLegacyModelConfigTable();

    /**
     * 创建多角色模型配置表。
     */
    void createModelConfigsTable();

    /**
     * 创建聊天会话表。
     */
    void createChatSessionsTable();

    /**
     * 创建聊天消息表。
     */
    void createChatMessagesTable();

    /**
     * 创建应用设置表。
     * <p>表结构采用 key-value，适合保存不归属于单个模型或会话的全局聊天设置。</p>
     */
    void createAppSettingsTable();

    /**
     * 创建图谱运行记录表。
     */
    void createKnowledgeGraphRunsTable();

    /**
     * 创建图谱 chunk 抽取缓存表。
     */
    void createKnowledgeGraphChunkExtractionsTable();

    /**
     * 创建图谱节点表。
     */
    void createKnowledgeGraphNodesTable();

    /**
     * 创建图谱边表。
     */
    void createKnowledgeGraphEdgesTable();

    /**
     * 创建图谱证据表。
     */
    void createKnowledgeGraphEvidenceTable();

    /**
     * 创建图谱前端视图表。
     */
    void createKnowledgeGraphViewsTable();

    /**
     * 读取 SQLite 表结构信息。
     *
     * @param tableName 表名，必须来自初始化器内部白名单
     * @return PRAGMA table_info 结果
     */
    List<Map<String, Object>> tableInfo(@Param("tableName") String tableName);

    /**
     * 为旧库补列。
     *
     * <p>SQLite 不支持用绑定参数替代标识符，调用方必须先校验 tableName、columnName 和 definition
     * 都是应用内固定迁移项。</p>
     *
     * @param tableName 表名，必须来自内部迁移清单
     * @param columnName 列名，必须来自内部迁移清单
     * @param definition 列定义，必须来自内部迁移清单
     */
    void addColumn(
            @Param("tableName") String tableName,
            @Param("columnName") String columnName,
            @Param("definition") String definition
    );

    /**
     * 创建知识库路径唯一索引。
     */
    void createKnowledgeFoldersPathIndex();

    /**
     * 创建知识库启用状态索引。
     */
    void createKnowledgeFoldersEnabledIndex();

    /**
     * 创建文档目录外键查询索引。
     */
    void createDocumentsKnowledgeFolderIdIndex();

    /**
     * 创建文档更新时间排序索引。
     */
    void createDocumentsUpdatedAtIndex();

    /**
     * 创建 chunk 文档 ID 查询索引。
     */
    void createChunksDocumentIdIndex();

    /**
     * 创建模型配置角色索引。
     */
    void createModelConfigsRoleIndex();

    /**
     * 创建模型配置角色 active 查询索引。
     */
    void createModelConfigsRoleActiveIndex();

    /**
     * 创建会话更新时间排序索引。
     */
    void createChatSessionsUpdatedAtIndex();

    /**
     * 创建会话消息 sequence 索引。
     */
    void createChatMessagesSequenceIndex();

    /**
     * 创建会话消息 conversationId 索引。
     */
    void createChatMessagesConversationIdIndex();

    /**
     * 创建图谱节点 scope/canonical 查询索引。
     */
    void createKnowledgeGraphNodesScopeCanonicalIndex();

    /**
     * 创建图谱边 scope 查询索引。
     */
    void createKnowledgeGraphEdgesScopeIndex();

    /**
     * 创建图谱边 scope 三元组去重索引。
     */
    void createKnowledgeGraphEdgesScopeTripleIndex();

    /**
     * 创建图谱证据 nodeId 索引。
     */
    void createKnowledgeGraphEvidenceNodeIndex();

    /**
     * 创建图谱证据 edgeId 索引。
     */
    void createKnowledgeGraphEvidenceEdgeIndex();

    /**
     * 创建图谱证据 chunkId 索引。
     */
    void createKnowledgeGraphEvidenceChunkIndex();

    /**
     * 创建图谱运行 scope/status 查询索引。
     */
    void createKnowledgeGraphRunsScopeStatusIndex();

    /**
     * 创建图谱视图 scope 查询索引。
     */
    void createKnowledgeGraphViewsScopeIndex();

    /**
     * 删除已软删除会话下的消息。
     */
    void deleteSoftDeletedChatMessages();

    /**
     * 物理清理已软删除会话。
     */
    void deleteSoftDeletedChatSessions();

    /**
     * 统计新模型配置表记录数。
     *
     * @return 配置数量
     */
    long countModelConfigs();

    /**
     * 读取旧版 active_model_config 表。
     *
     * <p>仅用于一次性迁移到 model_configs，迁移完成后运行时不应再依赖旧表。</p>
     *
     * @return 旧版配置行
     */
    List<Map<String, Object>> findLegacyActiveModelConfig();

    /**
     * 插入启动期初始模型配置。
     *
     * <p>该方法只由迁移或首次启动初始化调用，参数均来自默认配置或旧表清洗结果。</p>
     *
     * @param id 配置 ID
     * @param role 模型角色
     * @param provider 模型 Provider
     * @param displayName 展示名称
     * @param baseUrl Provider Base URL
     * @param apiKey API Key
     * @param modelName 模型名称
     * @param embeddingDimensions 向量维度
     * @param temperature 采样温度
     * @param defaultTopK 默认检索数量
     * @param contextWindowTokens 上下文窗口 token 数
     * @param createdAt 创建时间戳
     * @param updatedAt 更新时间戳
     */
    void insertInitialModelConfig(
            @Param("id") String id,
            @Param("role") String role,
            @Param("provider") String provider,
            @Param("displayName") String displayName,
            @Param("baseUrl") String baseUrl,
            @Param("apiKey") String apiKey,
            @Param("modelName") String modelName,
            @Param("embeddingDimensions") Integer embeddingDimensions,
            @Param("temperature") Double temperature,
            @Param("defaultTopK") Integer defaultTopK,
            @Param("contextWindowTokens") Integer contextWindowTokens,
            @Param("createdAt") long createdAt,
            @Param("updatedAt") long updatedAt
    );
}
