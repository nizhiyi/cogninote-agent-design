package com.itqianchen.agentdesign.domain.properties.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 聊天 Prompt 的类型化配置。
 *
 * <p>所有模板在启动期完成必填项和占位符校验，避免会话运行到模型调用阶段才暴露配置错误。</p>
 */
@ConfigurationProperties(prefix = "app.chat.prompts")
public record ChatPromptProperties(
        PromptTemplate general,
        Rag rag,
        QueryContextualizer queryContextualizer,
        ConnectionTest connectionTest
) {

    /**
     * 校验聊天 Prompt 的四组模板必须全部存在。
     *
     * <p>配置缺失属于启动期错误，直接失败比在某个聊天分支里降级更容易定位。</p>
     */
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
     * 普通聊天 Prompt 模板。
     *
     * <p>user 模板必须包含 {question}，否则无法把当前用户输入绑定到模型请求。</p>
     */
    public record PromptTemplate(
            String system,
            String user
    ) {

        /**
         * 校验普通聊天模板的必填文本和用户问题占位符。
         */
        public PromptTemplate {
            requireText(system, "app.chat.prompts.*.system");
            requireText(user, "app.chat.prompts.*.user");
            if (!user.contains("{question}")) {
                throw new IllegalArgumentException("app.chat.prompts.*.user must contain {question}");
            }
        }
    }

    /**
     * RAG 聊天 Prompt 模板。
     *
     * <p>知识库上下文由 Spring AI Advisor 注入，模板不能再声明 {context}，否则会形成两套上下文入口。</p>
     */
    public record Rag(
            String system,
            String user,
            String emptyContext
    ) {

        /**
         * 校验 RAG 模板和 Advisor 注入约束。
         */
        public Rag {
            requireText(system, "app.chat.prompts.rag.system");
            requireText(user, "app.chat.prompts.rag.user");
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
     * 查询改写 Prompt 模板。
     *
     * <p>模板必须同时接收当前问题和历史上下文，改写后的检索查询才不会丢失多轮会话指代。</p>
     */
    public record QueryContextualizer(
            String system,
            String user
    ) {

        /**
         * 校验查询改写模板所需的上下文占位符。
         */
        public QueryContextualizer {
            requireText(system, "app.chat.prompts.query-contextualizer.system");
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
     * 模型连通性测试 Prompt。
     *
     * <p>该模板应保持轻量，避免设置页测试连接时消耗过多上下文或触发业务型回复。</p>
     */
    public record ConnectionTest(String user) {

        /**
         * 校验连通性测试用户模板。
         */
        public ConnectionTest {
            requireText(user, "app.chat.prompts.connection-test.user");
        }
    }

    /**
     * 校验配置文本不能为空。
     *
     * @param value 配置值
     * @param propertyName 配置项名称，用于错误消息定位
     */
    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }
}
