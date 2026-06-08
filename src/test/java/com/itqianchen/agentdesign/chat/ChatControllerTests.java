package com.itqianchen.agentdesign.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.document.DocumentStatus;
import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.document.KnowledgeChunk;
import com.itqianchen.agentdesign.domain.document.KnowledgeDocument;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.search.EmbeddingGateway;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import reactor.core.publisher.Flux;

/**
 * Chat 控制器 测试 承担 聊天会话 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-chat-controller",
        "app.storage.database-path=target/test-cogninote-chat-controller/cogninote.db",
        "server.address=127.0.0.1"
})
class ChatControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModelConfigService modelConfigService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    @Autowired
    private KnowledgeStore knowledgeStore;

    @TempDir
    private Path tempDir;

    /**
     * 清理 clear State 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @BeforeEach
    void clearState() {
        databaseCleaner.clearAll();
    }

    /**
     * 执行 聊天会话 中的 chat Stream Requires Configured Model 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chatStreamRequiresConfiguredModel() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hello\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 执行 聊天会话 中的 chat Stream Returns Meta Delta And Done Events 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chatStreamReturnsMetaDeltaAndDoneEvents() throws Exception {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(tempDir.resolve("packaging.md"), "CogniNote uses Launch4j for Windows EXE packaging.");
        modelConfigService.save(new ModelConfigRequest(
                null,
                "DASHSCOPE",
                "DashScope",
                ModelConfigDefaults.BASE_URL,
                "sk-test",
                null,
                "qwen-plus",
                "text-embedding-v4",
                1024,
                0.7,
                8,
                null,
                ModelConfigDefaults.CONTEXT_WINDOW_TOKENS
        ));
        /**
         * 创建 insert Parsed Document 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        insertParsedDocument();
        knowledgeStore.rebuildAll();

        MvcResult start = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "How does packaging work?",
                                  "mode": "KEYWORD",
                                  "topK": 3
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(start))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        /**
         * 执行 聊天会话 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(body)
                .contains("event:meta")
                .contains("event:delta")
                .contains("event:done")
                .contains("contextUsage")
                .contains("packaging.md");
    }

    /**
     * 创建 insert Parsed Document 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    private void insertParsedDocument() {
        long now = System.currentTimeMillis();
        documentRepository.upsertDocument(new KnowledgeDocument(
                "doc-1",
                tempDir.resolve("packaging.md").toString(),
                "packaging.md",
                FileType.MARKDOWN,
                10,
                now,
                "hash",
                DocumentStatus.PARSED,
                now,
                now,
                now,
                1
        ));
        documentRepository.replaceChunks("doc-1", List.of(new KnowledgeChunk(
                "chunk-1",
                "doc-1",
                0,
                "CogniNote uses Launch4j for Windows EXE packaging.",
                "chunk-hash",
                null,
                "Packaging",
                12,
                now
        )));
    }

    /**
     * Fake Llm Configuration 集中维护 聊天会话 相关的 Spring 配置。
     * <p>这里的 Bean 或扫描配置会影响应用启动阶段的基础设施装配。</p>
     */
    @TestConfiguration
    static class FakeLlmConfiguration {

        /**
         * 执行 聊天会话 中的 fake Ai 运行时 工厂 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Bean
        @Primary
        AiRuntimeFactory fakeAiRuntimeFactory() {
            return new AiRuntimeFactory() {
                /**
                 * 执行 聊天会话 中的 chat 运行时 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                @Override
                public AiChatRuntime chatRuntime(ModelConfig config) {
                    return new AiChatRuntime() {
                        /**
                         * 启动 stream 流式流程。
                         * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
                         */
                        @Override
                        public Flux<String> stream(Prompt prompt) {
                            return Flux.just("可以使用 Launch4j 打包。");
                        }

                        /**
                         * 启动 stream 流式流程。
                         * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
                         */
                        @Override
                        public Flux<String> stream(
                                String systemPrompt,
                                String userMessage,
                                List<Advisor> advisors,
                                Map<String, Object> advisorParams
                        ) {
                            return Flux.just("可以使用 Launch4j 打包。");
                        }

                        /**
                         * 执行一次同步 call Text 调用。
                         * <p>外部模型响应会被转换为本地可处理的文本结果。</p>
                         */
                        @Override
                        public String callText(String systemPrompt, String userMessage) {
                            return "{\"shouldRewrite\":false,\"rewrittenQuery\":\"\",\"reason\":\"test_keep_original\",\"confidence\":0.0}";
                        }

                        /**
                         * 测试 test Connection 是否可用。
                         * <p>使用最小请求验证配置、网络和模型服务是否连通。</p>
                         */
                        @Override
                        public void testConnection(Prompt prompt) {
                        }
                    };
                }

                /**
                 * 执行 聊天会话 中的 embedding 运行时 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                @Override
                public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
                    throw new UnsupportedOperationException("Embedding runtime is intentionally disabled in chat controller tests");
                }
            };
        }

        /**
         * 执行 聊天会话 中的 fake Embedding 网关 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        @Bean
        @Primary
        EmbeddingGateway fakeEmbeddingGateway() {
            return new EmbeddingGateway() {
                /**
                 * 判断 is Available 条件是否成立。
                 * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
                 */
                @Override
                public boolean isAvailable() {
                    return false;
                }

                /**
                 * 执行 聊天会话 中的 dimensions 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                @Override
                public int dimensions() {
                    return 4;
                }

                /**
                 * 执行 聊天会话 中的 embed Documents 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                @Override
                public java.util.List<float[]> embedDocuments(java.util.List<String> texts) {
                    throw new UnsupportedOperationException("Embedding is intentionally disabled in chat controller tests");
                }

                /**
                 * 执行 聊天会话 中的 embed Query 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                @Override
                public float[] embedQuery(String query) {
                    throw new UnsupportedOperationException("Embedding is intentionally disabled in chat controller tests");
                }
            };
        }
    }
}
