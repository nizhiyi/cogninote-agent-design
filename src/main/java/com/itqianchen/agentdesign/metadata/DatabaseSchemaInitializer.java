package com.itqianchen.agentdesign.metadata;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        initialize();
    }

    public void initialize() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS documents (
                    id TEXT PRIMARY KEY,
                    source_path TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    file_type TEXT NOT NULL,
                    file_size INTEGER,
                    last_modified INTEGER,
                    content_hash TEXT,
                    status TEXT NOT NULL,
                    indexed_at INTEGER,
                    created_at INTEGER,
                    updated_at INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    content_hash TEXT NOT NULL,
                    page_number INTEGER,
                    heading TEXT,
                    token_count INTEGER,
                    created_at INTEGER,
                    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS model_config (
                    id TEXT PRIMARY KEY,
                    provider TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    base_url TEXT NOT NULL,
                    api_key TEXT,
                    chat_model TEXT NOT NULL,
                    embedding_model TEXT NOT NULL,
                    embedding_dimensions INTEGER NOT NULL,
                    temperature REAL NOT NULL,
                    top_k INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
        // 旧版本数据库中已经存在 model_config 时，CREATE TABLE 不会补列。
        // 这里显式做轻量迁移，保证用户本地 SQLite 能跟随阶段升级继续使用。
        addColumnIfMissing("model_config", "display_name", "TEXT NOT NULL DEFAULT 'DashScope'");
        addColumnIfMissing("model_config", "base_url",
                "TEXT NOT NULL DEFAULT 'https://dashscope.aliyuncs.com/api/v1'");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_documents_updated_at ON documents(updated_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON chunks(document_id)");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(" + tableName + ")");
        boolean exists = columns.stream()
                .map(column -> String.valueOf(column.get("name")))
                .anyMatch(columnName::equalsIgnoreCase);
        if (!exists) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
        }
    }
}


