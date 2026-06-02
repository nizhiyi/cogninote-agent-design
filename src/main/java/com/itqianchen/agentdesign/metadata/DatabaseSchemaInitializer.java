package com.itqianchen.agentdesign.metadata;

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
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_documents_updated_at ON documents(updated_at DESC)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_chunks_document_id ON chunks(document_id)");
    }
}
