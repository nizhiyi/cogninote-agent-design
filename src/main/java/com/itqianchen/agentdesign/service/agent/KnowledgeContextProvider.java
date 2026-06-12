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
 * 为知识库 Agent 获取可引用的检索上下文。
 *
 * <p>该服务负责向量不可用时的关键词降级，并在检索命中后回查 SQLite 补齐完整 chunk 内容，
 * 让前端来源抽屉和模型引用使用同一份持久化文本。</p>
 */
@Service
public class KnowledgeContextProvider {

    private final KnowledgeStore knowledgeStore;
    private final DocumentRepository documentRepository;

    /**
     * 注入检索索引和文档仓储。
     *
     * @param knowledgeStore 检索索引领域边界
     * @param documentRepository 文档事实源仓储
     */
    public KnowledgeContextProvider(
            KnowledgeStore knowledgeStore,
            DocumentRepository documentRepository
    ) {
        this.knowledgeStore = knowledgeStore;
        this.documentRepository = documentRepository;
    }

    /**
     * 获取知识库问答上下文。
     *
     * <p>返回的来源会补齐完整 chunk 内容，供 RAG prompt 和前端来源展示共用。</p>
     *
     * @param question 检索问题
     * @param requestedMode 请求检索模式
     * @param topK 检索数量
     * @return 知识库上下文
     */
    public KnowledgeContext retrieve(String question, SearchMode requestedMode, int topK) {
        SearchResponse searchResponse = searchWithFallback(question, requestedMode, topK);
        List<RagSourceResponse> sources = hydrateSources(toSources(searchResponse.hits()));
        return new KnowledgeContext(searchResponse.mode(), sources);
    }

    /**
     * 执行检索并在向量不可用时降级。
     *
     * @param question 检索问题
     * @param requestedMode 请求检索模式
     * @param topK 检索数量
     * @return 检索响应
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
     * 将搜索命中转换为 RAG 来源。
     *
     * @param hits 搜索命中
     * @return 带引用编号的来源列表
     */
    private List<RagSourceResponse> toSources(List<SearchHitResponse> hits) {
        List<RagSourceResponse> sources = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            sources.add(RagSourceResponse.from(index + 1, hits.get(index)));
        }
        return sources;
    }

    /**
     * 回查 SQLite 补齐来源正文。
     *
     * @param sources 搜索来源摘要
     * @return 带完整正文的来源
     */
    private List<RagSourceResponse> hydrateSources(List<RagSourceResponse> sources) {
        if (sources.isEmpty()) {
            return sources;
        }

        // 搜索命中只携带必要摘要；RAG prompt 需要完整 chunk，必须回到 SQLite 事实源补齐。
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
