package com.itqianchen.agentdesign.service.agent;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

/**
 * 为知识库问答构造最终交给模型的 RAG 查询。
 *
 * <p>该 augmenter 同时保留“用户原始问题”和“检索问题”。检索问题只用于补全省略语境，
 * 不能替代原始问题，否则多轮追问改写失败时会把回答带偏。</p>
 */
public final class CogninoteRagQueryAugmenter implements QueryAugmenter {

    private final String emptyContextPrompt;
    private final String originalQuestion;
    private final String retrievalQuery;

    /**
     * 绑定空上下文提示、原始问题和检索问题。
     *
     * @param emptyContextPrompt 无检索结果时注入的提示
     * @param originalQuestion 用户原始问题
     * @param retrievalQuery 用于检索的问题
     */
    public CogninoteRagQueryAugmenter(String emptyContextPrompt, String originalQuestion, String retrievalQuery) {
        this.emptyContextPrompt = emptyContextPrompt;
        this.originalQuestion = originalQuestion;
        this.retrievalQuery = retrievalQuery;
    }

    /**
     * 将 Spring AI 检索到的文档注入 prompt。
     *
     * <p>没有检索结果时也明确写入 emptyContextPrompt，让模型知道这是知识库为空，而不是系统漏传上下文。</p>
     *
     * @param query Spring AI 原始 Query
     * @param documents 检索文档
     * @return 注入知识库上下文后的 Query
     */
    @Override
    public Query augment(Query query, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return new Query("""
                    用户原始问题：
                    %s

                    知识库检索问题：
                    %s

                    知识库检索结果：
                    %s
                    
                    请回答“用户原始问题”。知识库检索问题只用于理解省略追问语境，不能替代用户原始问题。
                    """.formatted(originalQuestion, retrievalQuery, emptyContextPrompt));
        }

        return new Query("""
                用户原始问题：
                %s

                知识库检索问题：
                %s

                以下内容由 Spring AI RAG Advisor 注入。只有当片段与“用户原始问题 + 检索问题语境”相关时，才能作为回答依据：
                %s

                请回答“用户原始问题”。知识库检索问题只用于理解省略追问语境；如果检索片段与用户真实意图不一致，不要被片段带偏。
                请严格遵守系统消息中的 Markdown 格式要求，并用 [1]、[2] 这样的编号标注引用来源。
                """.formatted(originalQuestion, retrievalQuery, formatDocuments(documents)));
    }

    /**
     * 拼接 Spring AI Document 文本。
     *
     * @param documents 检索文档
     * @return 用空行分隔的上下文文本
     */
    private static String formatDocuments(List<Document> documents) {
        StringBuilder builder = new StringBuilder();
        for (Document document : documents) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(document.getText());
        }
        return builder.toString();
    }
}
