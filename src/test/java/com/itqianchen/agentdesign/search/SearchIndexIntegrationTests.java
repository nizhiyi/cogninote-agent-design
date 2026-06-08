package com.itqianchen.agentdesign.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itqianchen.agentdesign.service.document.DocumentIngestionService;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Search Index Integration 测试 承担 检索索引 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-search",
        "app.storage.database-path=target/test-cogninote-search/cogninote.db",
        "server.address=127.0.0.1"
})
class SearchIndexIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private DocumentRepository documentRepository;

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
     * 执行 检索索引 中的 ingest Markdown Automatically Indexes Keyword Search 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void ingestMarkdownAutomaticallyIndexesKeywordSearch() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("packaging.md"), "# 打包方案\n\nCogniNote 使用 Launch4j 生成 Windows EXE。");

        ingestionService.ingestFolder(tempDir.toString(), true);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Launch4j",
                                  "mode": "KEYWORD",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[0].fileName").value("packaging.md"))
                .andExpect(jsonPath("$.data.hits[0].preview").value(org.hamcrest.Matchers.containsString("Launch4j")));

        /**
         * 执行 检索索引 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(documentRepository.findAllOrderByUpdatedAtDesc())
                .singleElement()
                .satisfies(document -> assertThat(document.indexedAt()).isNotNull());
    }

    /**
     * 执行 检索索引 中的 keyword Search Hits Chinese Prose Code Identifiers And Mermaid Diagram 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void keywordSearchHitsChineseProseCodeIdentifiersAndMermaidDiagram() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("technical-note.md"), """
                # 多智能体路由

                路由式多智能体需要根据用户开关选择普通对话或知识库问答。

                ```java
                /**
                 * Chat 智能体 路由器 根据请求模式路由到对应的 检索索引 实现。
                 * <p>新增模式时优先在路由层扩展，不让调用方散落分支判断。</p>
                 */
                public class ChatAgentRouter {
                    private boolean useKnowledgeBase;

                    /**
                     * 执行 检索索引 中的 route To Knowledge Base 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    void routeToKnowledgeBase() {
                        useKnowledgeBase = true;
                    }
                }
                ```

                ```mermaid
                flowchart TD
                  User --> Router
                  Router --> KnowledgeBaseAgent
                ```
                """);
        ingestionService.ingestFolder(tempDir.toString(), true);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "多智能体 路由",
                                  "mode": "KEYWORD",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[0].fileName").value("technical-note.md"));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "ChatAgentRouter useKnowledgeBase",
                                  "mode": "KEYWORD",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[0].fileName").value("technical-note.md"));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "flowchart KnowledgeBaseAgent",
                                  "mode": "KEYWORD",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits[0].fileName").value("technical-note.md"));
    }

    /**
     * 执行 检索索引 中的 rebuild Uses Sqlite As Source Of Truth 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void rebuildUsesSqliteAsSourceOfTruth() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("rag.txt"), "SQLite is the source of truth and Lucene can be rebuilt.");
        ingestionService.ingestFolder(tempDir.toString(), true);
        knowledgeStore.rebuildAll();

        mockMvc.perform(get("/api/index/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexedDocumentCount").value(1))
                .andExpect(jsonPath("$.data.indexedChunkCount").value(1))
                .andExpect(jsonPath("$.data.unindexedDocumentCount").value(0));
    }

    /**
     * 删除 delete Document Removes Lucene Hit 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Test
    void deleteDocumentRemovesLuceneHit() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("delete-search.txt"), "This deleted document should not be searchable.");
        ingestionService.ingestFolder(tempDir.toString(), true);
        KnowledgeDocument document = documentRepository.findAllOrderByUpdatedAtDesc().getFirst();

        mockMvc.perform(delete("/api/documents/{id}", document.id()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "searchable",
                                  "mode": "KEYWORD",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hits.length()").value(0));
    }

    /**
     * 执行 检索索引 中的 vector And Hybrid Search Return Bad 请求 When Embedding Is Unavailable 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void vectorAndHybridSearchReturnBadRequestWhenEmbeddingIsUnavailable() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("keyword-only.txt"), "Keyword search works without an embedding model.");
        ingestionService.ingestFolder(tempDir.toString(), true);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "embedding",
                                  "mode": "VECTOR"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("vector search is unavailable")));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "embedding",
                                  "mode": "HYBRID"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("hybrid search is unavailable")));
    }
}


