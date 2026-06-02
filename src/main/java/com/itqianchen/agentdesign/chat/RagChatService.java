package com.itqianchen.agentdesign.chat;

import com.itqianchen.agentdesign.model.ModelConfig;
import com.itqianchen.agentdesign.model.ModelConfigService;
import com.itqianchen.agentdesign.document.DocumentRepository;
import com.itqianchen.agentdesign.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.search.KnowledgeStore;
import com.itqianchen.agentdesign.search.SearchHitResponse;
import com.itqianchen.agentdesign.search.SearchMode;
import com.itqianchen.agentdesign.search.SearchRequest;
import com.itqianchen.agentdesign.search.SearchResponse;
import com.itqianchen.agentdesign.search.StoredChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class RagChatService {

    private static final int MAX_CONTEXT_CHARS = 12000;

    private final KnowledgeStore knowledgeStore;
    private final DocumentRepository documentRepository;
    private final ModelConfigService modelConfigService;
    private final LlmGateway llmGateway;

    public RagChatService(
            KnowledgeStore knowledgeStore,
            DocumentRepository documentRepository,
            ModelConfigService modelConfigService,
            LlmGateway llmGateway
    ) {
        this.knowledgeStore = knowledgeStore;
        this.documentRepository = documentRepository;
        this.modelConfigService = modelConfigService;
        this.llmGateway = llmGateway;
    }

    public RagChatStream stream(ChatStreamRequest request) {
        ModelConfig config = modelConfigService.requireConfigured();
        String question = request.question().trim();
        int topK = normalizeTopK(request.topK(), config.topK());
        SearchMode requestedMode = request.mode() == null ? SearchMode.HYBRID : request.mode();
        SearchResponse searchResponse = searchWithFallback(question, requestedMode, topK);
        List<RagSourceResponse> sources = hydrateSources(toSources(searchResponse.hits()));
        Prompt prompt = buildPrompt(question, sources);

        return new RagChatStream(
                UUID.randomUUID().toString(),
                searchResponse.mode(),
                sources,
                llmGateway.stream(config, prompt)
        );
    }

    public Prompt buildPrompt(String question, List<RagSourceResponse> sources) {
        String systemPrompt = """
                你是 CogniNote Agent 的本地知识库问答助手。
                你必须只基于提供的知识库上下文回答，不要编造未出现的信息。
                如果上下文不足以回答，明确说明：当前知识库中没有足够依据。
                回答中必须用 [1]、[2] 这样的编号标注引用来源。
                """;
        String userPrompt = """
                用户问题：
                %s

                知识库上下文：
                %s

                请给出简洁、可验证的中文回答。
                """.formatted(question, buildContext(sources));

        return new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)));
    }

    private SearchResponse searchWithFallback(String question, SearchMode requestedMode, int topK) {
        try {
            return knowledgeStore.search(new SearchRequest(question, requestedMode, topK));
        } catch (EmbeddingUnavailableException ex) {
            if (requestedMode == SearchMode.HYBRID || requestedMode == SearchMode.VECTOR) {
                return knowledgeStore.search(new SearchRequest(question, SearchMode.KEYWORD, topK));
            }
            throw ex;
        }
    }

    private List<RagSourceResponse> toSources(List<SearchHitResponse> hits) {
        List<RagSourceResponse> sources = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            sources.add(RagSourceResponse.from(index + 1, hits.get(index)));
        }
        return sources;
    }

    private List<RagSourceResponse> hydrateSources(List<RagSourceResponse> sources) {
        if (sources.isEmpty()) {
            return sources;
        }

        Map<String, StoredChunk> chunksById = documentRepository.findStoredChunksByIds(sources.stream()
                        .map(RagSourceResponse::chunkId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(StoredChunk::chunkId, chunk -> chunk));

        return sources.stream()
                .map(source -> {
                    StoredChunk chunk = chunksById.get(source.chunkId());
                    if (chunk == null) {
                        return source;
                    }
                    return source.withContent(chunk.content());
                })
                .toList();
    }

    private String buildContext(List<RagSourceResponse> sources) {
        if (sources.isEmpty()) {
            return "没有检索到相关知识库片段。";
        }

        StringBuilder builder = new StringBuilder();
        for (RagSourceResponse source : sources) {
            String location = source.heading() != null && !source.heading().isBlank()
                    ? source.heading()
                    : source.pageNumber() == null ? "无标题" : "第 " + source.pageNumber() + " 页";
            String block = """
                    [%d] 文件：%s
                    路径：%s
                    位置：%s
                    内容：%s

                    """.formatted(
                    source.index(),
                    source.fileName(),
                    source.sourcePath(),
                    location,
                    source.content() == null || source.content().isBlank() ? source.preview() : source.content()
            );
            if (builder.length() + block.length() > MAX_CONTEXT_CHARS) {
                break;
            }
            builder.append(block);
        }
        return builder.toString();
    }

    private static int normalizeTopK(Integer requestedTopK, int configuredTopK) {
        int value = requestedTopK == null ? configuredTopK : requestedTopK;
        return Math.clamp(value, 1, 50);
    }
}
