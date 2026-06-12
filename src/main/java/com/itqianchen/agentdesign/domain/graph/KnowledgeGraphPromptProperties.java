package com.itqianchen.agentdesign.domain.graph;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识图谱 Prompt 配置。
 * <p>Prompt 文本和版本号都来自配置文件，避免模型规则散落在服务代码里。</p>
 */
@ConfigurationProperties(prefix = "app.knowledge-graph.prompts")
public record KnowledgeGraphPromptProperties(
        Extraction extraction
) {

    /**
     * 校验图谱抽取 Prompt 配置必须存在。
     */
    public KnowledgeGraphPromptProperties {
        if (extraction == null) {
            throw new IllegalArgumentException("app.knowledge-graph.prompts.extraction must be configured");
        }
    }

    /**
     * 单 chunk 抽取 Prompt。
     * <p>version 会参与抽取缓存 key；修改抽取语义时应同步升级版本。</p>
     */
    public record Extraction(
            String version,
            String system,
            String user
    ) {

        /**
         * 校验图谱抽取模板的必填项和所有运行期占位符。
         *
         * <p>缺少任一占位符都会让抽取结果失去文档来源或 chunk 边界，必须在启动期失败。</p>
         */
        public Extraction {
            requireText(version, "app.knowledge-graph.prompts.extraction.version");
            requireText(system, "app.knowledge-graph.prompts.extraction.system");
            requireText(user, "app.knowledge-graph.prompts.extraction.user");
            requirePlaceholder(user, "app.knowledge-graph.prompts.extraction.user", "{documentName}");
            requirePlaceholder(user, "app.knowledge-graph.prompts.extraction.user", "{chunkId}");
            requirePlaceholder(user, "app.knowledge-graph.prompts.extraction.user", "{heading}");
            requirePlaceholder(user, "app.knowledge-graph.prompts.extraction.user", "{pageNumber}");
            requirePlaceholder(user, "app.knowledge-graph.prompts.extraction.user", "{content}");
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

    /**
     * 校验 Prompt 模板必须包含指定占位符。
     *
     * @param value Prompt 模板文本
     * @param propertyName 配置项名称，用于错误消息定位
     * @param placeholder 必须存在的占位符
     */
    private static void requirePlaceholder(String value, String propertyName, String placeholder) {
        if (!value.contains(placeholder)) {
            throw new IllegalArgumentException(propertyName + " must contain " + placeholder);
        }
    }
}
