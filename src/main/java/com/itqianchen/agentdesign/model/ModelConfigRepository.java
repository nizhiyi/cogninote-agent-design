package com.itqianchen.agentdesign.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ModelConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ModelConfig> findActive() {
        List<ModelConfig> configs = jdbcTemplate.query("""
                        SELECT id, provider, api_key, chat_model, embedding_model,
                               embedding_dimensions, temperature, top_k, created_at, updated_at
                        FROM model_config
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapConfig(rs),
                ModelConfigDefaults.ACTIVE_CONFIG_ID
        );
        return configs.stream().findFirst();
    }

    public ModelConfig saveActive(ModelConfig config) {
        jdbcTemplate.update("""
                        INSERT INTO model_config (
                            id, provider, api_key, chat_model, embedding_model,
                            embedding_dimensions, temperature, top_k, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            provider = excluded.provider,
                            api_key = excluded.api_key,
                            chat_model = excluded.chat_model,
                            embedding_model = excluded.embedding_model,
                            embedding_dimensions = excluded.embedding_dimensions,
                            temperature = excluded.temperature,
                            top_k = excluded.top_k,
                            updated_at = excluded.updated_at
                        """,
                config.id(),
                config.provider().name(),
                config.apiKey(),
                config.chatModel(),
                config.embeddingModel(),
                config.embeddingDimensions(),
                config.temperature(),
                config.topK(),
                config.createdAt(),
                config.updatedAt()
        );
        return findActive().orElseThrow(() ->
                new IllegalStateException("Model config was not found after save"));
    }

    private static ModelConfig mapConfig(ResultSet rs) throws SQLException {
        return new ModelConfig(
                rs.getString("id"),
                ModelProvider.valueOf(rs.getString("provider")),
                rs.getString("api_key"),
                rs.getString("chat_model"),
                rs.getString("embedding_model"),
                rs.getInt("embedding_dimensions"),
                rs.getDouble("temperature"),
                rs.getInt("top_k"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
