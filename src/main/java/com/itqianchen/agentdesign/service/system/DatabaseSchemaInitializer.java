package com.itqianchen.agentdesign.service.system;

import com.itqianchen.agentdesign.mapper.schema.DatabaseSchemaMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化和轻量迁移本地 SQLite schema。
 *
 * <p>这里只执行幂等 DDL 和明确白名单内的 ADD COLUMN，避免桌面应用升级时意外改写用户数据。</p>
 */
@Component
public class DatabaseSchemaInitializer implements ApplicationListener<ApplicationReadyEvent>, Ordered {

    /**
     * 允许自动执行的列迁移。
     *
     * <p>SQLite 的 ALTER TABLE 语义有限，新增列必须显式列在白名单里，防止调用方传入任意 DDL。</p>
     */
    private static final Map<String, String> ALLOWED_COLUMN_MIGRATIONS = Map.of(
            "documents.knowledge_folder_id", "TEXT",
            "model_config.display_name", "TEXT NOT NULL DEFAULT 'DashScope'",
            "model_config.base_url", "TEXT NOT NULL DEFAULT 'https://dashscope.aliyuncs.com/api/v1'",
            "model_configs.context_window_tokens", "INTEGER",
            "chat_messages.agent_type", "TEXT"
    );

    private final DatabaseSchemaMapper databaseSchemaMapper;

    /**
     * 注入 schema 初始化 Mapper。
     *
     * @param databaseSchemaMapper 启动期 DDL 和迁移 SQL Mapper
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
     * 执行幂等建表、补列、索引创建和旧配置迁移。
     */
    public void initialize() {
        databaseSchemaMapper.createKnowledgeFoldersTable();
        databaseSchemaMapper.createDocumentsTable();
        databaseSchemaMapper.createChunksTable();
        databaseSchemaMapper.createLegacyModelConfigTable();
        // 旧版本数据库中已经存在 model_config 时，CREATE TABLE 不会补列。
        // 这里显式做轻量迁移，保证用户本地 SQLite 能跟随阶段升级继续使用。
        addColumnIfMissing("documents", "knowledge_folder_id", "TEXT");
        addColumnIfMissing("model_config", "display_name", "TEXT NOT NULL DEFAULT 'DashScope'");
        addColumnIfMissing("model_config", "base_url",
                "TEXT NOT NULL DEFAULT 'https://dashscope.aliyuncs.com/api/v1'");
        databaseSchemaMapper.createModelConfigsTable();
        addColumnIfMissing("model_configs", "context_window_tokens", "INTEGER");
        databaseSchemaMapper.createChatSessionsTable();
        databaseSchemaMapper.createChatMessagesTable();
        /*
         * 第 23 阶段开始，追问补全策略属于全局聊天设置。
         * 使用独立 key-value 表，避免把非模型参数塞进 model_configs。
         */
        databaseSchemaMapper.createAppSettingsTable();
        databaseSchemaMapper.createKnowledgeGraphRunsTable();
        databaseSchemaMapper.createKnowledgeGraphChunkExtractionsTable();
        databaseSchemaMapper.createKnowledgeGraphNodesTable();
        databaseSchemaMapper.createKnowledgeGraphEdgesTable();
        databaseSchemaMapper.createKnowledgeGraphEvidenceTable();
        databaseSchemaMapper.createKnowledgeGraphViewsTable();
        addColumnIfMissing("chat_messages", "agent_type", "TEXT");
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
        databaseSchemaMapper.createKnowledgeGraphRunsScopeStatusIndex();
        databaseSchemaMapper.createKnowledgeGraphViewsScopeIndex();
        cleanupSoftDeletedChatSessions();
        migrateLegacyModelConfigIfNeeded();
    }

    /**
     * 清理旧版本软删除会话的物理残留。
     */
    private void cleanupSoftDeletedChatSessions() {
        /*
         * 旧版本的“删除会话”只是把 chat_sessions.deleted 置为 1，消息仍留在本地库里。
         * 新版本把用户删除视为销毁操作；启动时顺手清掉历史软删除残留，避免升级后旧数据继续存在。
         */
        databaseSchemaMapper.deleteSoftDeletedChatMessages();
        databaseSchemaMapper.deleteSoftDeletedChatSessions();
    }

    /**
     * 在白名单允许范围内为旧表补列。
     *
     * @param tableName 表名
     * @param columnName 列名
     * @param definition 列定义
     */
    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        String migrationKey = tableName + "." + columnName;
        String allowedDefinition = ALLOWED_COLUMN_MIGRATIONS.get(migrationKey);
        if (!definition.equals(allowedDefinition)) {
            throw new IllegalArgumentException("Unsupported column migration: " + migrationKey);
        }
        // 表结构读取结果在不同 SQLite/JDBC 版本里 key 大小写不稳定，sqliteColumnName 做兼容归一。
        List<Map<String, Object>> columns = databaseSchemaMapper.tableInfo(tableName);
        boolean exists = columns.stream()
                .map(DatabaseSchemaInitializer::sqliteColumnName)
                .anyMatch(existingColumn -> existingColumn != null && columnName.equalsIgnoreCase(existingColumn));
        if (!exists) {
            databaseSchemaMapper.addColumn(tableName, columnName, definition);
        }
    }

    /**
     * 从 PRAGMA table_info 行里读取列名。
     *
     * @param column PRAGMA 返回行
     * @return 列名；缺失时返回 null
     */
    private static String sqliteColumnName(Map<String, Object> column) {
        for (Map.Entry<String, Object> entry : column.entrySet()) {
            if ("name".equalsIgnoreCase(entry.getKey())) {
                Object value = entry.getValue();
                return value == null ? null : String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 在新模型配置表为空时迁移旧 active_model_config。
     */
    private void migrateLegacyModelConfigIfNeeded() {
        if (databaseSchemaMapper.countModelConfigs() > 0) {
            return;
        }
        List<Map<String, Object>> legacyRows = databaseSchemaMapper.findLegacyActiveModelConfig();
        Map<String, Object> legacy = legacyRows.isEmpty() ? Map.of() : legacyRows.getFirst();
        long now = System.currentTimeMillis();
        long createdAt = longValue(legacy.get("created_at"), now);
        long updatedAt = longValue(legacy.get("updated_at"), now);
        String provider = textValue(legacy.get("provider"), "DASHSCOPE");
        String baseUrl = textValue(legacy.get("base_url"), "https://dashscope.aliyuncs.com/api/v1");
        String apiKey = textValue(legacy.get("api_key"), "");

        /*
         * Phase 8 把“一个 active 配置同时管 Chat 和 Embedding”拆成两个 active 配置。
         * 迁移只在新表为空时执行，避免用户后续新建的多配置被旧表覆盖。
         */
        insertInitialModelConfig(
                "active-chat",
                "CHAT",
                provider,
                textValue(legacy.get("display_name"), "DashScope Chat"),
                baseUrl,
                apiKey,
                textValue(legacy.get("chat_model"), "qwen-plus"),
                null,
                doubleObjectValue(legacy.get("temperature"), 0.7),
                intObjectValue(legacy.get("top_k"), 8),
                128_000,
                createdAt,
                updatedAt
        );
        insertInitialModelConfig(
                "active-embedding",
                "EMBEDDING",
                provider,
                textValue(legacy.get("display_name"), "DashScope Embedding"),
                baseUrl,
                apiKey,
                textValue(legacy.get("embedding_model"), "text-embedding-v4"),
                intObjectValue(legacy.get("embedding_dimensions"), 1024),
                null,
                null,
                null,
                createdAt,
                updatedAt
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
                id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
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

    /**
     * 读取文本值并提供默认值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 非空文本
     */
    private static String textValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? defaultValue : text;
    }

    /**
     * 读取 long 值并提供默认值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return long 值
     */
    private static long longValue(Object value, long defaultValue) {
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    /**
     * 读取 Integer 值并提供默认值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return Integer 值
     */
    private static Integer intObjectValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    /**
     * 读取 Double 值并提供默认值。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return Double 值
     */
    private static Double doubleObjectValue(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }
}
