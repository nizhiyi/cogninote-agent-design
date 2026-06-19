package com.itqianchen.agentdesign.service.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.mapper.schema.DatabaseSchemaMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.sqlite.SQLiteDataSource;

class DatabaseSchemaInitializerTests {

    @Test
    void initializeAddsKnowledgeGraphEdgeDisplayLabelAndRebuildsUniqueIndex() {
        try (SqlSession sqlSession = sqliteSqlSession()) {
            DatabaseSchemaMapper mapper = sqlSession.getMapper(DatabaseSchemaMapper.class);
            createLegacyKnowledgeGraphEdgesTable(sqlSession);
            new DatabaseSchemaInitializer(mapper).initialize();

            assertThat(columnNames(mapper.tableInfo("knowledge_graph_edges"))).contains("display_label");
            assertThat(indexColumnNames(sqlSession, "idx_kg_edges_scope_triple")).containsExactly(
                    "scope_type",
                    "scope_id",
                    "source_node_id",
                    "target_node_id",
                    "relation_type",
                    "display_label"
            );
            assertThat(indexColumnNames(sqlSession, "idx_kg_edges_scope_triple_migration")).isEmpty();
        }
    }

    private static void createLegacyKnowledgeGraphEdgesTable(SqlSession sqlSession) {
        try (Statement statement = sqlSession.getConnection().createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE knowledge_graph_edges (
                        id TEXT PRIMARY KEY,
                        scope_type TEXT NOT NULL,
                        scope_id TEXT,
                        source_node_id TEXT NOT NULL,
                        target_node_id TEXT NOT NULL,
                        relation_type TEXT NOT NULL,
                        description TEXT,
                        confidence REAL NOT NULL DEFAULT 0,
                        mention_count INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE UNIQUE INDEX idx_kg_edges_scope_triple
                    ON knowledge_graph_edges(scope_type, scope_id, source_node_id, target_node_id, relation_type)
                    """);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to create legacy knowledge graph edge schema", ex);
        }
    }

    private static List<String> indexColumnNames(SqlSession sqlSession, String indexName) {
        List<String> names = new ArrayList<>();
        try (Statement statement = sqlSession.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA index_info(" + indexName + ")")) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to inspect SQLite index: " + indexName, ex);
        }
        return names;
    }

    private static List<String> columnNames(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(DatabaseSchemaInitializerTests::sqliteColumnName)
                .toList();
    }

    private static String sqliteColumnName(Map<String, Object> row) {
        return row.entrySet().stream()
                .filter(entry -> "name".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(String::valueOf)
                .findFirst()
                .orElse("");
    }

    private static SqlSession sqliteSqlSession() {
        try {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite::memory:");
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:/mappers/*.xml"));
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("Failed to create test MyBatis SqlSessionFactory");
            }
            return factory.openSession(true);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create in-memory SQLite MyBatis session", ex);
        }
    }
}
