package com.itqianchen.agentdesign.domain.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat Prompt 配置属性 映射 聊天会话 的 YAML 配置。
 * <p>通过类型化配置隔离环境变量、默认值和业务代码。</p>
 */
@ConfigurationProperties(prefix = "app.chat.prompts")
public record ChatPromptProperties(
        PromptTemplate general,
        Rag rag,
        QueryContextualizer queryContextualizer,
        ConnectionTest connectionTest
) {

    public ChatPromptProperties {
        if (general == null) {
            throw new IllegalArgumentException("app.chat.prompts.general must be configured");
        }
        if (rag == null) {
            throw new IllegalArgumentException("app.chat.prompts.rag must be configured");
        }
        if (queryContextualizer == null) {
            throw new IllegalArgumentException("app.chat.prompts.query-contextualizer must be configured");
        }
        if (connectionTest == null) {
            throw new IllegalArgumentException("app.chat.prompts.connection-test must be configured");
        }
    }

    /**
     * Prompt Template 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    public record PromptTemplate(
            String system,
            String user
    ) {

        public PromptTemplate {
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(system, "app.chat.prompts.*.system");
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(user, "app.chat.prompts.*.user");
            if (!user.contains("{question}")) {
                throw new IllegalArgumentException("app.chat.prompts.*.user must contain {question}");
            }
        }
    }

    /**
     * Rag 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    public record Rag(
            String system,
            String user,
            String emptyContext
    ) {

        public Rag {
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(system, "app.chat.prompts.rag.system");
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(user, "app.chat.prompts.rag.user");
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(emptyContext, "app.chat.prompts.rag.empty-context");
            /*
             * 第十三阶段后知识库上下文由 Spring AI RAG Advisor 注入，
             * user 模板只负责承载当前用户问题，不能再要求 {context} 占位符。
             */
            if (!user.contains("{question}")) {
                throw new IllegalArgumentException(
                        "app.chat.prompts.rag.user must contain {question}");
            }
            if (user.contains("{context}")) {
                throw new IllegalArgumentException(
                        "app.chat.prompts.rag.user must not contain {context}; RAG context is injected by Spring AI Advisor");
            }
        }
    }

    /**
     * Query Contextualizer 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    public record QueryContextualizer(
            String system,
            String user
    ) {

        public QueryContextualizer {
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(system, "app.chat.prompts.query-contextualizer.system");
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(user, "app.chat.prompts.query-contextualizer.user");
            if (!user.contains("{question}")) {
                throw new IllegalArgumentException(
                        "app.chat.prompts.query-contextualizer.user must contain {question}");
            }
            if (!user.contains("{history}")) {
                throw new IllegalArgumentException(
                        "app.chat.prompts.query-contextualizer.user must contain {history}");
            }
        }
    }

    /**
     * Connection 测试 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    public record ConnectionTest(String user) {

        public ConnectionTest {
            /**
             * 读取必需的 require Text 配置或数据。
             * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
             */
            requireText(user, "app.chat.prompts.connection-test.user");
        }
    }

    /**
     * 读取必需的 require Text 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }
}
