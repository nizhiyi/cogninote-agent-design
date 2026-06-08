package com.itqianchen.agentdesign.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itqianchen.agentdesign.service.document.DocumentIngestionService;
import com.itqianchen.agentdesign.domain.search.EmbeddingGateway;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Search Vector Integration 测试 承担 检索索引 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-vector-search",
        "app.storage.database-path=target/test-cogninote-vector-search/cogninote.db",
        "server.address=127.0.0.1"
})
class SearchVectorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private KnowledgeStore knowledgeStore;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @TempDir
    private Path tempDir;

    /**
     * 清理 clear State 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @BeforeEach
    void clearState() {
        databaseCleaner.clearDocuments();
        knowledgeStore.rebuildAll();
    }

    /**
     * 执行 检索索引 中的 vector And Hybrid Search Use Configured Embedding 网关 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void vectorAndHybridSearchUseConfiguredEmbeddingGateway() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("alpha.txt"), "alpha memory vector payload");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("beta.txt"), "beta unrelated payload");
        ingestionService.ingestFolder(tempDir.toString(), true);

        mockMvc.perform(get("/api/index/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embeddingConfigured").value(true));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "alpha question",
                                  "mode": "VECTOR",
                                  "topK": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[0].fileName").value("alpha.txt"));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "alpha payload",
                                  "mode": "HYBRID",
                                  "topK": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[0].fileName").value("alpha.txt"));
    }

    /**
     * 执行 检索索引 中的 embedding 网关 Uses Document And Query Purposes 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void embeddingGatewayUsesDocumentAndQueryPurposes() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("purpose.txt"), "purpose alpha payload");
        ingestionService.ingestFolder(tempDir.toString(), true);
        RecordingEmbeddingGateway gateway = (RecordingEmbeddingGateway) knowledgeStoreStatusAwareGateway();

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "purpose alpha",
                                  "mode": "VECTOR",
                                  "topK": 1
                                }
                                """))
                .andExpect(status().isOk());

        /**
         * 执行 检索索引 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(gateway.documentCalls()).isGreaterThan(0);
        /**
         * 执行 检索索引 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(gateway.queryCalls()).isGreaterThan(0);
    }

    @Autowired
    private EmbeddingGateway knowledgeStoreStatusAwareGateway;

    /**
     * 执行 检索索引 中的 knowledge 存储 Status Aware 网关 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private EmbeddingGateway knowledgeStoreStatusAwareGateway() {
        return knowledgeStoreStatusAwareGateway;
    }

    /**
     * Fake Embedding Configuration 集中维护 检索索引 相关的 Spring 配置。
     * <p>这里的 Bean 或扫描配置会影响应用启动阶段的基础设施装配。</p>
     */
    @TestConfiguration
    static class FakeEmbeddingConfiguration {

        /**
         * 执行 检索索引 中的 fake Embedding 网关 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Bean
        @Primary
        EmbeddingGateway fakeEmbeddingGateway() {
            return new RecordingEmbeddingGateway();
        }
    }

    /**
     * Recording Embedding 网关 将基础设施能力适配为 检索索引 领域网关。
     * <p>领域层通过网关调用外部能力，避免直接耦合具体 SDK。</p>
     */
    private static final class RecordingEmbeddingGateway implements EmbeddingGateway {
        private int documentCalls;
        private int queryCalls;

        /**
         * 判断 is Available 条件是否成立。
         * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
         */
        @Override
        public boolean isAvailable() {
            return true;
        }

        /**
         * 执行 检索索引 中的 dimensions 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Override
        public int dimensions() {
            return 4;
        }

        /**
         * 执行 检索索引 中的 embed Documents 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Override
        public List<float[]> embedDocuments(List<String> texts) {
            documentCalls++;
            return texts.stream()
                    .map(this::embed)
                    .toList();
        }

        /**
         * 执行 检索索引 中的 embed Query 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Override
        public float[] embedQuery(String query) {
            queryCalls++;
            return embed(query);
        }

        /**
         * 执行 检索索引 中的 document Calls 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private int documentCalls() {
            return documentCalls;
        }

        /**
         * 执行 检索索引 中的 query Calls 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private int queryCalls() {
            return queryCalls;
        }

        /**
         * 执行 检索索引 中的 embed 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        private float[] embed(String text) {
            String lowerCaseText = text.toLowerCase();
            if (lowerCaseText.contains("alpha") || lowerCaseText.contains("purpose")) {
                return new float[]{1.0f, 0.0f, 0.0f, 0.0f};
            }
            if (lowerCaseText.contains("beta")) {
                return new float[]{0.0f, 1.0f, 0.0f, 0.0f};
            }
            return new float[]{0.0f, 0.0f, 1.0f, 0.0f};
        }
    }
}


