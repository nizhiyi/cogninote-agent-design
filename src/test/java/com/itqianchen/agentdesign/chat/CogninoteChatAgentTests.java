package com.itqianchen.agentdesign.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.ai.AiChatRuntime;
import com.itqianchen.agentdesign.domain.ai.AiEmbeddingRuntime;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.domain.search.IndexedDocument;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.domain.search.StoredChunk;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.dto.search.SearchHitResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import com.itqianchen.agentdesign.repository.model.ModelConfigRepository;
import com.itqianchen.agentdesign.service.agent.CogninoteChatAgent;
import com.itqianchen.agentdesign.service.agent.ConversationMemoryPort;
import com.itqianchen.agentdesign.service.agent.KnowledgeContext;
import com.itqianchen.agentdesign.service.agent.KnowledgeContextProvider;
import com.itqianchen.agentdesign.service.agent.NoopConversationMemoryPort;
import com.itqianchen.agentdesign.service.agent.PromptAssembler;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

class CogninoteChatAgentTests {

    @Test
    void promptContainsQuestionContextAndSourceNumbers() {
        PromptAssembler promptAssembler = new PromptAssembler(defaultPromptProperties());
        String context = """
                [1] 文件：packaging.md
                路径：D:/notes/packaging.md
                位置：打包
                内容：CogniNote 使用 Launch4j 生成 Windows EXE。
                """;

        Prompt prompt = promptAssembler.assembleRagPrompt("如何打包？", context);

        assertThat(prompt.getContents())
                .contains("如何打包？")
                .contains("[1]")
                .contains("packaging.md")
                .contains("Launch4j");
    }

    @Test
    void promptUsesConfiguredTemplates() {
        ChatPromptProperties promptProperties = new ChatPromptProperties(
                new ChatPromptProperties.Rag(
                        "自定义系统提示词",
                        "Q={question}\nCTX={context}",
                        "自定义空上下文"
                ),
                new ChatPromptProperties.ConnectionTest("测试连接")
        );
        PromptAssembler promptAssembler = new PromptAssembler(promptProperties);

        Prompt prompt = promptAssembler.assembleRagPrompt("没有资料的问题", promptProperties.rag().emptyContext());

        assertThat(prompt.getContents())
                .contains("自定义系统提示词")
                .contains("Q=没有资料的问题")
                .contains("CTX=自定义空上下文");
    }

    @Test
    void hybridFallsBackToKeywordWhenEmbeddingIsUnavailable() {
        FakeKnowledgeStore knowledgeStore = new FakeKnowledgeStore(true);
        CogninoteChatAgent agent = newAgent(knowledgeStore, defaultPromptProperties());

        AgentChatStream stream = agent.stream(new AgentRequest(
                null,
                "Launch4j 是什么？",
                5,
                SearchMode.HYBRID,
                null,
                true
        ));

        assertThat(stream.retrievalMode()).isEqualTo(SearchMode.KEYWORD);
        assertThat(stream.sources()).hasSize(1);
        assertThat(stream.answer().collectList().block()).containsExactly("答案片段");
        assertThat(knowledgeStore.seenModes).containsExactly(SearchMode.HYBRID, SearchMode.KEYWORD);
    }

    @Test
    void knowledgeContextHydratesSourceContentFromSqliteChunks() {
        KnowledgeContextProvider provider = new KnowledgeContextProvider(
                new FakeKnowledgeStore(false),
                new FakeDocumentRepository(),
                defaultPromptProperties()
        );

        KnowledgeContext context = provider.retrieve("如何打包？", SearchMode.KEYWORD, 3);

        assertThat(context.contextText())
                .contains("[1]")
                .contains("packaging.md")
                .contains("CogniNote 使用 Launch4j 生成 Windows EXE。");
        assertThat(context.sources())
                .singleElement()
                .satisfies(source -> assertThat(source.content()).contains("Launch4j"));
    }

    @Test
    void assistantMessageIsSavedOnlyAfterModelStreamCompletes() {
        RecordingConversationMemoryPort memory = new RecordingConversationMemoryPort();
        CogninoteChatAgent agent = newAgent(
                new FakeKnowledgeStore(false),
                defaultPromptProperties(),
                new FakeAiRuntimeFactory(Flux.just("答案", "片段")),
                memory
        );

        AgentChatStream stream = agent.stream(new AgentRequest(
                "request-1",
                "Launch4j 是什么？",
                5,
                SearchMode.KEYWORD,
                "conversation-1",
                true
        ));

        assertThat(memory.userMessages).containsExactly("Launch4j 是什么？");
        assertThat(memory.assistantMessages).isEmpty();

        assertThat(stream.answer().collectList().block()).containsExactly("答案", "片段");
        assertThat(memory.assistantMessages).containsExactly("答案片段");
    }

    @Test
    void cancelledModelStreamDoesNotSaveAssistantMessageAsCompleteAnswer() {
        RecordingConversationMemoryPort memory = new RecordingConversationMemoryPort();
        CogninoteChatAgent agent = newAgent(
                new FakeKnowledgeStore(false),
                defaultPromptProperties(),
                new FakeAiRuntimeFactory(Flux.concat(Flux.just("半截"), Flux.never())),
                memory
        );

        AgentChatStream stream = agent.stream(new AgentRequest(
                "request-2",
                "Launch4j 是什么？",
                5,
                SearchMode.KEYWORD,
                "conversation-2",
                true
        ));
        List<String> received = new ArrayList<>();

        Disposable subscription = stream.answer().subscribe(received::add);
        subscription.dispose();

        assertThat(received).containsExactly("半截");
        assertThat(memory.assistantMessages).isEmpty();
    }

    private static CogninoteChatAgent newAgent(KnowledgeStore knowledgeStore, ChatPromptProperties promptProperties) {
        return newAgent(
                knowledgeStore,
                promptProperties,
                new FakeAiRuntimeFactory(),
                new NoopConversationMemoryPort()
        );
    }

    private static CogninoteChatAgent newAgent(
            KnowledgeStore knowledgeStore,
            ChatPromptProperties promptProperties,
            AiRuntimeFactory aiRuntimeFactory,
            ConversationMemoryPort conversationMemoryPort
    ) {
        ModelConfigRepository repository = new ModelConfigRepository(null) {
            @Override
            public Optional<ModelConfig> findActive(ModelConfigRole role) {
                long now = System.currentTimeMillis();
                return Optional.of(new ModelConfig(
                        ModelConfigDefaults.ACTIVE_CHAT_CONFIG_ID,
                        ModelConfigRole.CHAT,
                        ModelConfigDefaults.PROVIDER,
                        ModelConfigDefaults.DISPLAY_NAME,
                        ModelConfigDefaults.BASE_URL,
                        "sk-test",
                        ModelConfigDefaults.CHAT_MODEL,
                        null,
                        ModelConfigDefaults.TEMPERATURE,
                        ModelConfigDefaults.TOP_K,
                        true,
                        now,
                        now
                ));
            }
        };
        return new CogninoteChatAgent(
                new ModelConfigService(repository),
                aiRuntimeFactory,
                new KnowledgeContextProvider(knowledgeStore, new FakeDocumentRepository(), promptProperties),
                new PromptAssembler(promptProperties),
                conversationMemoryPort
        );
    }

    private static ChatPromptProperties defaultPromptProperties() {
        return new ChatPromptProperties(
                new ChatPromptProperties.Rag(
                        """
                                你是 CogniNote Agent 的本地知识库问答助手。
                                你必须只基于提供的知识库上下文回答，不要编造未出现的信息。
                                如果上下文不足以回答，明确说明：当前知识库中没有足够依据。
                                回答中必须用 [1]、[2] 这样的编号标注引用来源。
                                """,
                        """
                                用户问题：
                                {question}

                                知识库上下文：
                                {context}

                                请给出简洁、可验证的中文回答。
                                """,
                        "没有检索到相关知识库片段。"
                ),
                new ChatPromptProperties.ConnectionTest("请用一句话回答：CogniNote 连接测试是否可用？")
        );
    }

    private static SearchHitResponse hit(String chunkId) {
        return new SearchHitResponse(
                chunkId,
                "doc-1",
                "packaging.md",
                "D:/notes/packaging.md",
                "打包",
                null,
                "CogniNote 使用 Launch4j 生成 Windows EXE。",
                1.0,
                1.0,
                null
        );
    }

    private static class FakeKnowledgeStore implements KnowledgeStore {
        private final boolean failHybrid;
        private final List<SearchMode> seenModes = new ArrayList<>();

        private FakeKnowledgeStore(boolean failHybrid) {
            this.failHybrid = failHybrid;
        }

        @Override
        public void indexDocument(IndexedDocument document) {
        }

        @Override
        public void deleteByDocumentId(String documentId) {
        }

        @Override
        public RebuildIndexResponse rebuildByDocumentIds(List<IndexedDocument> documents) {
            return null;
        }

        @Override
        public SearchResponse search(SearchRequest request) {
            seenModes.add(request.modeOrDefault());
            if (failHybrid && request.modeOrDefault() == SearchMode.HYBRID) {
                throw new EmbeddingUnavailableException("hybrid search is unavailable");
            }
            return new SearchResponse(request.query(), request.modeOrDefault(), List.of(hit("chunk-1")));
        }

        @Override
        public IndexStatusResponse status() {
            return null;
        }

        @Override
        public RebuildIndexResponse rebuildAll() {
            return null;
        }
    }

    private static class FakeAiRuntimeFactory implements AiRuntimeFactory {
        private final Flux<String> stream;

        private FakeAiRuntimeFactory() {
            this(Flux.just("答案片段"));
        }

        private FakeAiRuntimeFactory(Flux<String> stream) {
            this.stream = stream;
        }

        @Override
        public AiChatRuntime chatRuntime(ModelConfig config) {
            return new AiChatRuntime() {
                @Override
                public Flux<String> stream(Prompt prompt) {
                    return stream;
                }

                @Override
                public void testConnection(Prompt prompt) {
                }
            };
        }

        @Override
        public AiEmbeddingRuntime embeddingRuntime(ModelConfig config) {
            throw new UnsupportedOperationException("Embedding runtime is not used in this test");
        }
    }

    private static class RecordingConversationMemoryPort implements ConversationMemoryPort {
        private final List<String> userMessages = new ArrayList<>();
        private final List<String> assistantMessages = new ArrayList<>();

        @Override
        public List<Message> loadRecentMessages(String conversationId, int maxMessages) {
            return List.of();
        }

        @Override
        public void saveUserMessage(String conversationId, String content) {
            userMessages.add(content);
        }

        @Override
        public void saveAssistantMessage(String conversationId, String content) {
            assistantMessages.add(content);
        }
    }

    private static class FakeDocumentRepository extends DocumentRepository {

        private FakeDocumentRepository() {
            super(null);
        }

        @Override
        public List<StoredChunk> findStoredChunksByIds(List<String> chunkIds) {
            return chunkIds.stream()
                    .map(chunkId -> new StoredChunk(
                            chunkId,
                            "doc-1",
                            0,
                            "CogniNote 使用 Launch4j 生成 Windows EXE。",
                            "hash",
                            null,
                            "打包",
                            "packaging.md",
                            "D:/notes/packaging.md"
                    ))
                    .toList();
        }
    }
}
