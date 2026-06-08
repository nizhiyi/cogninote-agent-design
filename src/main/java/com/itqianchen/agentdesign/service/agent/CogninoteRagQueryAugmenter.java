package com.itqianchen.agentdesign.service.agent;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;

/**
 * Cogninote Rag Query Augmenter 承担 智能体编排 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
public final class CogninoteRagQueryAugmenter implements QueryAugmenter {

    private final String emptyContextPrompt;
    private final String originalQuestion;
    private final String retrievalQuery;

    /**
     * 注入 CogninoteRagQueryAugmenter 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public CogninoteRagQueryAugmenter(String emptyContextPrompt, String originalQuestion, String retrievalQuery) {
        this.emptyContextPrompt = emptyContextPrompt;
        this.originalQuestion = originalQuestion;
        this.retrievalQuery = retrievalQuery;
    }

    /**
     * 执行 智能体编排 中的 augment 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 执行 智能体编排 中的 format Documents 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
