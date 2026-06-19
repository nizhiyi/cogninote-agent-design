package com.itqianchen.agentdesign.service.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphEdge;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphScope;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphScopeType;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphView;
import com.itqianchen.agentdesign.domain.graph.KnowledgeGraphViewType;
import com.itqianchen.agentdesign.dto.graph.KnowledgeGraphViewResponse;
import com.itqianchen.agentdesign.repository.graph.KnowledgeGraphRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KnowledgeGraphServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FakeKnowledgeGraphRepository repository = new FakeKnowledgeGraphRepository();
    private final KnowledgeGraphService service = new KnowledgeGraphService(
            repository,
            null,
            null,
            null,
            null,
            null,
            null,
            Runnable::run,
            new GraphCanonicalizer(),
            objectMapper
    );

    @Test
    void viewEnrichesLegacyGraphPayloadWithDisplayLabelAndCoarseType() {
        long now = 1780000000000L;
        repository.view = new KnowledgeGraphView(
                "view-1",
                KnowledgeGraphScopeType.ALL,
                null,
                KnowledgeGraphViewType.GRAPH,
                """
                        {
                          "viewType": "GRAPH",
                          "nodes": [],
                          "edges": [
                            {
                              "id":"edge-1",
                              "source":"node-a",
                              "target":"node-b",
                              "sourceLabel":"Redis",
                              "targetLabel":"Sentinel",
                              "label":"USES",
                              "description":"Redis uses Sentinel"
                            },
                            {
                              "id":"edge-2",
                              "source":"node-b",
                              "target":"node-c",
                              "sourceLabel":"Sentinel",
                              "targetLabel":"客户端",
                              "label":"Notifies About"
                            }
                          ]
                        }
                        """,
                "run-1",
                now,
                now
        );
        repository.edges = List.of(new KnowledgeGraphEdge(
                "edge-1",
                KnowledgeGraphScopeType.ALL,
                null,
                "node-a",
                "node-b",
                "FUNCTIONAL",
                "使用",
                "Redis uses Sentinel",
                0.88,
                2,
                now,
                now
        ));

        KnowledgeGraphViewResponse response = service.view("ALL", null, "GRAPH");

        JsonNode firstEdge = response.payload().path("edges").get(0);
        assertThat(firstEdge.path("label").asText()).isEqualTo("FUNCTIONAL");
        assertThat(firstEdge.path("displayLabel").asText()).isEqualTo("使用");
        assertThat(firstEdge.path("description").asText()).isEqualTo("Redis 使用 Sentinel。");
        JsonNode secondEdge = response.payload().path("edges").get(1);
        assertThat(secondEdge.path("label").asText()).isEqualTo("CAUSAL");
        assertThat(secondEdge.path("displayLabel").asText()).isEqualTo("相关");
        assertThat(secondEdge.path("description").asText()).isEqualTo("Sentinel 相关 客户端。");
    }

    private static final class FakeKnowledgeGraphRepository extends KnowledgeGraphRepository {
        private KnowledgeGraphView view;
        private List<KnowledgeGraphEdge> edges = List.of();

        private FakeKnowledgeGraphRepository() {
            super(null);
        }

        @Override
        public Optional<KnowledgeGraphView> findView(KnowledgeGraphScope scope, String viewType) {
            return Optional.ofNullable(view);
        }

        @Override
        public List<KnowledgeGraphEdge> findEdgesByScope(KnowledgeGraphScope scope) {
            return edges;
        }
    }
}
