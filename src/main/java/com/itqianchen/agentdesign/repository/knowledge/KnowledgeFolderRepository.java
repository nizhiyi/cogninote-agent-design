package com.itqianchen.agentdesign.repository.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolder;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeFolderRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeFolderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeFolderSummary> findAllSummaries() {
        return jdbcTemplate.query("""
                        SELECT
                            f.*,
                            COUNT(d.id) AS document_count,
                            SUM(CASE WHEN d.status = 'PARSED' THEN 1 ELSE 0 END) AS parsed_count,
                            SUM(CASE WHEN d.status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                            COALESCE(SUM(CASE WHEN d.status = 'PARSED' THEN d.chunk_count ELSE 0 END), 0) AS chunk_count,
                            SUM(CASE WHEN d.status = 'PARSED' AND d.indexed_at IS NULL THEN 1 ELSE 0 END) AS unindexed_count
                        FROM knowledge_folders f
                        LEFT JOIN (
                            SELECT d.*, COUNT(c.id) AS chunk_count
                            FROM documents d
                            LEFT JOIN chunks c ON c.document_id = d.id
                            GROUP BY d.id
                        ) d ON d.knowledge_folder_id = f.id
                        GROUP BY f.id
                        ORDER BY f.updated_at DESC
                        """,
                (rs, rowNum) -> mapSummary(rs)
        );
    }

    public Optional<KnowledgeFolder> findById(String id) {
        List<KnowledgeFolder> folders = jdbcTemplate.query("""
                        SELECT *
                        FROM knowledge_folders
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapFolder(rs),
                id
        );
        return folders.stream().findFirst();
    }

    public Optional<KnowledgeFolder> findByFolderPath(String folderPath) {
        List<KnowledgeFolder> folders = jdbcTemplate.query("""
                        SELECT *
                        FROM knowledge_folders
                        WHERE folder_path = ?
                        """,
                (rs, rowNum) -> mapFolder(rs),
                folderPath
        );
        return folders.stream().findFirst();
    }

    public void upsert(KnowledgeFolder folder) {
        jdbcTemplate.update("""
                        INSERT INTO knowledge_folders (
                            id, folder_path, display_name, recursive, enabled,
                            last_ingested_at, last_indexed_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(folder_path) DO UPDATE SET
                            display_name = excluded.display_name,
                            recursive = excluded.recursive,
                            enabled = excluded.enabled,
                            last_ingested_at = excluded.last_ingested_at,
                            last_indexed_at = excluded.last_indexed_at,
                            updated_at = excluded.updated_at
                        """,
                folder.id(),
                folder.folderPath(),
                folder.displayName(),
                folder.recursive() ? 1 : 0,
                folder.enabled() ? 1 : 0,
                folder.lastIngestedAt(),
                folder.lastIndexedAt(),
                folder.createdAt(),
                folder.updatedAt()
        );
    }

    public void updateEnabled(String id, boolean enabled, long updatedAt) {
        jdbcTemplate.update("""
                        UPDATE knowledge_folders
                        SET enabled = ?, updated_at = ?
                        WHERE id = ?
                        """,
                enabled ? 1 : 0,
                updatedAt,
                id
        );
    }

    public void markIngested(String id, long timestamp) {
        jdbcTemplate.update("""
                        UPDATE knowledge_folders
                        SET last_ingested_at = ?, updated_at = ?
                        WHERE id = ?
                        """,
                timestamp,
                timestamp,
                id
        );
    }

    public void markIndexed(String id, long timestamp) {
        jdbcTemplate.update("""
                        UPDATE knowledge_folders
                        SET last_indexed_at = ?, updated_at = ?
                        WHERE id = ?
                        """,
                timestamp,
                timestamp,
                id
        );
    }

    public boolean deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM knowledge_folders WHERE id = ?", id) > 0;
    }

    private static KnowledgeFolderSummary mapSummary(ResultSet rs) throws SQLException {
        return new KnowledgeFolderSummary(
                mapFolder(rs),
                rs.getInt("document_count"),
                rs.getInt("parsed_count"),
                rs.getInt("failed_count"),
                rs.getInt("chunk_count"),
                rs.getInt("unindexed_count")
        );
    }

    private static KnowledgeFolder mapFolder(ResultSet rs) throws SQLException {
        return new KnowledgeFolder(
                rs.getString("id"),
                rs.getString("folder_path"),
                rs.getString("display_name"),
                rs.getInt("recursive") == 1,
                rs.getInt("enabled") == 1,
                getNullableLong(rs, "last_ingested_at"),
                getNullableLong(rs, "last_indexed_at"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private static Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }
}
