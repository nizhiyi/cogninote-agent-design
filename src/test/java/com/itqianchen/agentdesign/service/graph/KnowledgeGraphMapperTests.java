package com.itqianchen.agentdesign.service.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphScopeType;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphView;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphViewType;
import com.itqianchen.agentdesign.mapper.graph.KnowledgeGraphMapper;
import com.itqianchen.agentdesign.mapper.graph.KnowledgeGraphSummaryRow;
import com.itqianchen.agentdesign.mapper.schema.DatabaseSchemaMapper;
import java.util.List;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.sqlite.SQLiteDataSource;

class KnowledgeGraphMapperTests {

    @Test
    void findGeneratedGraphSummariesDeduplicatesViewsByScope() {
        try (SqlSession sqlSession = sqliteSqlSession()) {
            DatabaseSchemaMapper schemaMapper = sqlSession.getMapper(DatabaseSchemaMapper.class);
            KnowledgeGraphMapper graphMapper = sqlSession.getMapper(KnowledgeGraphMapper.class);
            schemaMapper.createKnowledgeGraphViewsTable();
            long now = 1780000000000L;

            graphMapper.insertView(view(
                    "view-mindmap",
                    "folder-1",
                    KnowledgeGraphViewType.MINDMAP,
                    now - 100
            ));
            graphMapper.insertView(view(
                    "view-graph",
                    "folder-1",
                    KnowledgeGraphViewType.GRAPH,
                    now
            ));

            List<KnowledgeGraphSummaryRow> rows = graphMapper.findGeneratedGraphSummaries();

            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().scopeType()).isEqualTo("KNOWLEDGE_FOLDER");
            assertThat(rows.getFirst().scopeId()).isEqualTo("folder-1");
            assertThat(rows.getFirst().mindmapReady()).isTrue();
            assertThat(rows.getFirst().graphReady()).isTrue();
            assertThat(rows.getFirst().generatedAt()).isEqualTo(now);
        }
    }

    private static KnowledgeGraphView view(
            String id,
            String scopeId,
            KnowledgeGraphViewType viewType,
            long updatedAt
    ) {
        return new KnowledgeGraphView(
                id,
                KnowledgeGraphScopeType.KNOWLEDGE_FOLDER,
                scopeId,
                viewType,
                "{\"viewType\":\"" + viewType.name() + "\"}",
                "run-1",
                updatedAt - 1000,
                updatedAt
        );
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
