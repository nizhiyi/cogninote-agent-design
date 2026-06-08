package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
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
     * 注入 CogninoteDocumentRetriever 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
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
     * 执行 智能体编排 中的 original Question 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String originalQuestion() {
        return originalQuestion;
    }

    /**
     * 执行 智能体编排 中的 retrieval Query 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String retrievalQuery() {
        return retrievalQuery;
    }

    /**
     * 执行 智能体编排 中的 retrieve Knowledge Context 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public KnowledgeContext retrieveKnowledgeContext() {
        return context();
    }

    /**
     * 执行 智能体编排 中的 retrieve 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public List<Document> retrieve(Query query) {
        return context().sources().stream()
                .map(CogninoteDocumentRetriever::toDocument)
                .toList();
    }

    /**
     * 执行 智能体编排 中的 context 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private KnowledgeContext context() {
        KnowledgeContext existing = cachedContext;
        if (existing != null) {
            return existing;
        }
        /**
         * 执行 智能体编排 中的 synchronized 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
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
     * 执行 智能体编排 中的 to Document 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 执行 智能体编排 中的 document Text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 执行 智能体编排 中的 metadata 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
        /**
         * 执行 智能体编排 中的 put If Not Null 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        putIfNotNull(metadata, "documentId", source.documentId());
        /**
         * 执行 智能体编排 中的 put If Not Null 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        putIfNotNull(metadata, "fileName", source.fileName());
        /**
         * 执行 智能体编排 中的 put If Not Null 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        putIfNotNull(metadata, "sourcePath", source.sourcePath());
        /**
         * 执行 智能体编排 中的 put If Not Null 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        putIfNotNull(metadata, "heading", source.heading());
        /**
         * 执行 智能体编排 中的 put If Not Null 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        putIfNotNull(metadata, "pageNumber", source.pageNumber());
        return metadata;
    }

    /**
     * 执行 智能体编排 中的 put If Not Null 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
