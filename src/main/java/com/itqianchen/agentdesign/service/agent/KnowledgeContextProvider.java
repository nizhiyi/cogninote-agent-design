package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import com.itqianchen.agentdesign.domain.search.EmbeddingUnavailableException;
import com.itqianchen.agentdesign.domain.search.KnowledgeStore;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.domain.search.StoredChunk;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import com.itqianchen.agentdesign.dto.search.SearchHitResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import com.itqianchen.agentdesign.repository.document.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeContextProvider {

    private static final int MAX_CONTEXT_CHARS = 12000;

    private final KnowledgeStore knowledgeStore;
    private final DocumentRepository documentRepository;
    private final ChatPromptProperties promptProperties;

    public KnowledgeContextProvider(
            KnowledgeStore knowledgeStore,
            DocumentRepository documentRepository,
            ChatPromptProperties promptProperties
    ) {
        this.knowledgeStore = knowledgeStore;
        this.documentRepository = documentRepository;
        this.promptProperties = promptProperties;
    }

    public KnowledgeContext retrieve(String question, SearchMode requestedMode, int topK) {
        SearchResponse searchResponse = searchWithFallback(question, requestedMode, topK);
        List<RagSourceResponse> sources = hydrateSources(toSources(searchResponse.hits()));
        return new KnowledgeContext(searchResponse.mode(), sources, buildContext(sources));
    }

    private SearchResponse searchWithFallback(String question, SearchMode requestedMode, int topK) {
        try {
            return knowledgeStore.search(new SearchRequest(question, requestedMode, topK));
        } catch (EmbeddingUnavailableException ex) {
            if (requestedMode == SearchMode.HYBRID || requestedMode == SearchMode.VECTOR) {
                /*
                 * 向量/混合检索依赖 active Embedding runtime。
                 * 未配置或不可用时，RAG 仍降级到关键词检索，并通过 meta 返回实际检索模式。
                 */
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
            return promptProperties.rag().emptyContext();
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
                // 保持 Prompt 上下文预算集中在知识上下文层，后续接重排或压缩时只改这里。
                break;
            }
            builder.append(block);
        }
        return builder.toString();
    }
}
