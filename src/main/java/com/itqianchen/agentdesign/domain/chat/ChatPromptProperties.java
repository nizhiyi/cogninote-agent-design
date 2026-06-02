package com.itqianchen.agentdesign.domain.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.chat.prompts")
public record ChatPromptProperties(
        Rag rag,
        ConnectionTest connectionTest
) {

    public ChatPromptProperties {
        if (rag == null) {
            throw new IllegalArgumentException("app.chat.prompts.rag must be configured");
        }
        if (connectionTest == null) {
            throw new IllegalArgumentException("app.chat.prompts.connection-test must be configured");
        }
    }

    public record Rag(
            String system,
            String user,
            String emptyContext
    ) {

        public Rag {
            requireText(system, "app.chat.prompts.rag.system");
            requireText(user, "app.chat.prompts.rag.user");
            requireText(emptyContext, "app.chat.prompts.rag.empty-context");
            /*
             * RAG 用户提示词由配置文件管理，但问题和上下文必须由运行时填充。
             * 启动期校验占位符，能避免后续改提示词时把知识库上下文静默丢掉。
             */
            if (!user.contains("{question}") || !user.contains("{context}")) {
                throw new IllegalArgumentException(
                        "app.chat.prompts.rag.user must contain {question} and {context}");
            }
        }
    }

    public record ConnectionTest(String user) {

        public ConnectionTest {
            requireText(user, "app.chat.prompts.connection-test.user");
        }
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must not be blank");
        }
    }
}
