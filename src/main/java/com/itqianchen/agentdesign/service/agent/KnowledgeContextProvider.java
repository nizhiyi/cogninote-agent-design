package com.itqianchen.agentdesign.service.agent;

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

/**
 * Knowledge Context Provider 承担 智能体编排 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@Service
public class KnowledgeContextProvider {

    private final KnowledgeStore knowledgeStore;
    private final DocumentRepository documentRepository;

    /**
     * 注入 KnowledgeContextProvider 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public KnowledgeContextProvider(
            KnowledgeStore knowledgeStore,
            DocumentRepository documentRepository
    ) {
        this.knowledgeStore = knowledgeStore;
        this.documentRepository = documentRepository;
    }

    /**
     * 执行 智能体编排 中的 retrieve 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public KnowledgeContext retrieve(String question, SearchMode requestedMode, int topK) {
        SearchResponse searchResponse = searchWithFallback(question, requestedMode, topK);
        List<RagSourceResponse> sources = hydrateSources(toSources(searchResponse.hits()));
        return new KnowledgeContext(searchResponse.mode(), sources);
    }

    /**
     * 执行 智能体编排 中的 search With Fallback 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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

    /**
     * 执行 智能体编排 中的 to Sources 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<RagSourceResponse> toSources(List<SearchHitResponse> hits) {
        List<RagSourceResponse> sources = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            sources.add(RagSourceResponse.from(index + 1, hits.get(index)));
        }
        return sources;
    }

    /**
     * 执行 智能体编排 中的 hydrate Sources 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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

}
