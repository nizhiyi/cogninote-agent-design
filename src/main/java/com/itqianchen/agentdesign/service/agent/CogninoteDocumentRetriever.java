package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import com.itqianchen.agentdesign.domain.dto.chat.RagSourceResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;

/**
 * Cogninote Document Retriever 负责检索 智能体编排 上下文。
 * <p>检索结果会进入提示词，因此需要保留来源和排序信息。</p>
 */
public final class CogninoteDocumentRetriever implements DocumentRetriever {

    private final KnowledgeContextProvider knowledgeContextProvider;
    private final String originalQuestion;
    private final String retrievalQuery;
    private final SearchMode requestedMode;
    private final int topK;
    private volatile KnowledgeContext cachedContext;

    /**
     * 创建单轮 RAG 文档检索器。
     *
     * @param knowledgeContextProvider 知识库上下文提供者
     * @param originalQuestion 用户原始问题
     * @param retrievalQuery 用于检索的问题；为空时回退原始问题
     * @param requestedMode 请求检索模式
     * @param topK 检索数量
     */
    public CogninoteDocumentRetriever(
            KnowledgeContextProvider knowledgeContextProvider,
            String originalQuestion,
            String retrievalQuery,
            SearchMode requestedMode,
            int topK
    ) {
        this.knowledgeContextProvider = knowledgeContextProvider;
        this.originalQuestion = originalQuestion;
        this.retrievalQuery = retrievalQuery == null || retrievalQuery.isBlank()
                ? originalQuestion
                : retrievalQuery;
        this.requestedMode = requestedMode;
        this.topK = topK;
    }

    /**
     * 返回用户原始问题。
     *
     * @return 原始问题
     */
    public String originalQuestion() {
        return originalQuestion;
    }

    /**
     * 返回实际检索 query。
     *
     * @return 检索 query
     */
    public String retrievalQuery() {
        return retrievalQuery;
    }

    /**
     * 读取并缓存本轮知识库上下文。
     *
     * @return 知识库上下文
     */
    public KnowledgeContext retrieveKnowledgeContext() {
        return context();
    }

    /**
     * 给 Spring AI RAG Advisor 返回检索文档。
     *
     * <p>query 参数由 Advisor 传入，但真实检索已使用构造器中的 retrievalQuery，避免格式化 prompt 被误用为检索词。</p>
     *
     * @param query Spring AI Query
     * @return RAG 文档列表
     */
    @Override
    public List<Document> retrieve(Query query) {
        return context().sources().stream()
                .map(CogninoteDocumentRetriever::toDocument)
                .toList();
    }

    /**
     * 获取本轮检索上下文并做惰性缓存。
     *
     * @return 知识库上下文
     */
    private KnowledgeContext context() {
        KnowledgeContext existing = cachedContext;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (cachedContext == null) {
                /*
                 * ChatClient 的 user(...) 里包含格式化提示词，不适合直接作为检索 query。
                 * DocumentRetriever 仍挂在 Spring AI RAG Advisor 链上，检索 query 由补全 Agent
                 * 在必要时结合历史生成，但最终回答仍面向用户原始问题。
                 */
                cachedContext = knowledgeContextProvider.retrieve(retrievalQuery, requestedMode, topK);
            }
            return cachedContext;
        }
    }

    /**
     * 将 RAG 来源转换为 Spring AI Document。
     *
     * @param source RAG 来源
     * @return Spring AI Document
     */
    private static Document toDocument(RagSourceResponse source) {
        return Document.builder()
                .id(source.chunkId())
                .text(documentText(source))
                .metadata(metadata(source))
                .score(source.score())
                .build();
    }

    /**
     * 构造模型可读的引用文本。
     *
     * @param source RAG 来源
     * @return 带编号、路径和位置的文档文本
     */
    private static String documentText(RagSourceResponse source) {
        String content = source.content() == null || source.content().isBlank()
                ? source.preview()
                : source.content();
        String location = source.heading() != null && !source.heading().isBlank()
                ? source.heading()
                : source.pageNumber() == null ? "无标题" : "第 " + source.pageNumber() + " 页";
        /*
         * 交给 Spring AI RAG Advisor 的 Document 也保留引用编号和路径信息。
         * 这样模型能按前端 meta 的 [1]/[2] 编号回应，而不是只看到裸文本片段。
         */
        return """
                [%d] 文件：%s
                路径：%s
                位置：%s
                内容：%s
                """.formatted(
                source.index(),
                source.fileName(),
                source.sourcePath(),
                location,
                content == null ? "" : content
        );
    }

    /**
     * 构造 Spring AI Document metadata。
     *
     * @param source RAG 来源
     * @return 不含 null 值的 metadata
     */
    private static Map<String, Object> metadata(RagSourceResponse source) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sourceIndex", source.index());
        metadata.put("score", source.score());
        /*
         * Spring AI Document 会拒绝 null metadata value。
         * heading/pageNumber 这类来源字段本来就是可选的，缺失时应省略 metadata，
         * 不能把 null 透传到 RetrievalAugmentationAdvisor 链路里。
         */
        putIfNotNull(metadata, "chunkId", source.chunkId());
        putIfNotNull(metadata, "documentId", source.documentId());
        putIfNotNull(metadata, "fileName", source.fileName());
        putIfNotNull(metadata, "sourcePath", source.sourcePath());
        putIfNotNull(metadata, "heading", source.heading());
        putIfNotNull(metadata, "pageNumber", source.pageNumber());
        return metadata;
    }

    /**
     * metadata 中只放非空值。
     *
     * @param metadata metadata Map
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
