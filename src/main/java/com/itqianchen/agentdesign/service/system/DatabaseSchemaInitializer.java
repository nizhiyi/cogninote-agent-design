package com.itqianchen.agentdesign.service.system;

import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.mapper.schema.DatabaseSchemaMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化本地 SQLite schema 和基础种子数据。
 *
 * <p>当前版本是新的数据库基线；初始化只面向空库或已是当前结构的库，不再执行测试版历史 schema 迁移。</p>
 */
@Component
public class DatabaseSchemaInitializer implements ApplicationListener<ApplicationReadyEvent>, Ordered {

    private final DatabaseSchemaMapper databaseSchemaMapper;

    /**
     * 注入 schema 初始化 Mapper。
     *
     * @param databaseSchemaMapper 启动期 DDL 和种子数据 SQL Mapper
     */
    public DatabaseSchemaInitializer(DatabaseSchemaMapper databaseSchemaMapper) {
        this.databaseSchemaMapper = databaseSchemaMapper;
    }

    /**
     * 在应用就绪后初始化 SQLite schema。
     *
     * @param event Spring Boot 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initialize();
    }

    /**
     * 返回最高优先级，保证 schema 先于其他启动清理执行。
     *
     * @return Ordered.HIGHEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        // Schema 必须先于各业务模块的启动清理运行，避免新表尚未创建就被查询。
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 执行幂等建表、索引创建和默认模型配置初始化。
     */
    public void initialize() {
        databaseSchemaMapper.createKnowledgeFoldersTable();
        databaseSchemaMapper.createDocumentsTable();
        databaseSchemaMapper.createChunksTable();
        databaseSchemaMapper.createModelConfigsTable();
        databaseSchemaMapper.createChatSessionsTable();
        databaseSchemaMapper.createChatMessagesTable();
        databaseSchemaMapper.createAppSettingsTable();
        databaseSchemaMapper.createKnowledgeFolderRunsTable();
        databaseSchemaMapper.createKnowledgeGraphRunsTable();
        databaseSchemaMapper.createKnowledgeGraphChunkExtractionsTable();
        databaseSchemaMapper.createKnowledgeGraphNodesTable();
        databaseSchemaMapper.createKnowledgeGraphEdgesTable();
        databaseSchemaMapper.createKnowledgeGraphEvidenceTable();
        databaseSchemaMapper.createKnowledgeGraphViewsTable();
        databaseSchemaMapper.createKnowledgeFoldersPathIndex();
        databaseSchemaMapper.createKnowledgeFoldersEnabledIndex();
        databaseSchemaMapper.createDocumentsKnowledgeFolderIdIndex();
        databaseSchemaMapper.createDocumentsUpdatedAtIndex();
        databaseSchemaMapper.createChunksDocumentIdIndex();
        databaseSchemaMapper.createModelConfigsRoleIndex();
        databaseSchemaMapper.createModelConfigsRoleActiveIndex();
        databaseSchemaMapper.createChatSessionsUpdatedAtIndex();
        databaseSchemaMapper.createChatMessagesSequenceIndex();
        databaseSchemaMapper.createChatMessagesConversationIdIndex();
        databaseSchemaMapper.createKnowledgeGraphNodesScopeCanonicalIndex();
        databaseSchemaMapper.createKnowledgeGraphEdgesScopeIndex();
        databaseSchemaMapper.createKnowledgeGraphEdgesScopeTripleIndex();
        databaseSchemaMapper.createKnowledgeGraphEvidenceNodeIndex();
        databaseSchemaMapper.createKnowledgeGraphEvidenceEdgeIndex();
        databaseSchemaMapper.createKnowledgeGraphEvidenceChunkIndex();
        databaseSchemaMapper.createKnowledgeFolderRunsScopeIndex();
        databaseSchemaMapper.createKnowledgeFolderRunsOperationIndex();
        databaseSchemaMapper.createKnowledgeFolderRunsStatusIndex();
        databaseSchemaMapper.createKnowledgeGraphRunsScopeStatusIndex();
        databaseSchemaMapper.createKnowledgeGraphViewsScopeIndex();
        initializeDefaultModelConfigsIfEmpty();
    }

    /**
     * 为空库写入默认 Chat 和 Embedding 模型配置。
     */
    private void initializeDefaultModelConfigsIfEmpty() {
        if (databaseSchemaMapper.countModelConfigs() > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        insertInitialModelConfig(
                ModelConfigDefaults.ACTIVE_CHAT_CONFIG_ID,
                ModelConfigRole.CHAT.name(),
                ModelConfigDefaults.PROVIDER.name(),
                ModelConfigDefaults.CHAT_DISPLAY_NAME,
                ModelConfigDefaults.BASE_URL,
                "",
                ModelConfigDefaults.CHAT_MODEL,
                null,
                ModelConfigDefaults.TEMPERATURE,
                ModelConfigDefaults.TOP_K,
                ModelConfigDefaults.CONTEXT_WINDOW_TOKENS,
                now,
                now
        );
        insertInitialModelConfig(
                ModelConfigDefaults.ACTIVE_EMBEDDING_CONFIG_ID,
                ModelConfigRole.EMBEDDING.name(),
                ModelConfigDefaults.PROVIDER.name(),
                ModelConfigDefaults.EMBEDDING_DISPLAY_NAME,
                ModelConfigDefaults.BASE_URL,
                "",
                ModelConfigDefaults.EMBEDDING_MODEL,
                ModelConfigDefaults.EMBEDDING_DIMENSIONS,
                null,
                null,
                null,
                now,
                now
        );
    }

    /**
     * 插入启动期初始模型配置。
     *
     * @param id 配置 ID
     * @param role 模型角色
     * @param provider Provider 名称
     * @param displayName 展示名称
     * @param baseUrl Base URL
     * @param apiKey API Key
     * @param modelName 模型名称
     * @param embeddingDimensions 向量维度
     * @param temperature 温度
     * @param defaultTopK 默认 topK
     * @param contextWindowTokens 上下文窗口 token
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    private void insertInitialModelConfig(
            String id,
            String role,
            String provider,
            String displayName,
            String baseUrl,
            String apiKey,
            String modelName,
            Integer embeddingDimensions,
            Double temperature,
            Integer defaultTopK,
            Integer contextWindowTokens,
            long createdAt,
            long updatedAt
    ) {
        databaseSchemaMapper.insertInitialModelConfig(
                id,
                role,
                provider,
                displayName,
                baseUrl,
                apiKey,
                modelName,
                embeddingDimensions,
                temperature,
                defaultTopK,
                contextWindowTokens,
                createdAt,
                updatedAt
        );
    }
}
