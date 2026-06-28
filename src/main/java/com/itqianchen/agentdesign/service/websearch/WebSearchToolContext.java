package com.itqianchen.agentdesign.service.websearch;

import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;

/**
 * WebSearchTools 从 Spring AI ToolContext 中读取的请求级上下文。
 *
 * <p>ToolContext 不进入模型可见 schema，适合传递 API Key 快照和收集器这类后端运行态对象。</p>
 */
record WebSearchToolContext(
        WebSearchSettingsSnapshot settings,
        ToolExecutionCollector collector
) {
    static final String SETTINGS_KEY = "webSearchSettings";
    static final String COLLECTOR_KEY = "toolExecutionCollector";

    static WebSearchToolContext from(ToolContext toolContext) {
        Map<String, Object> context = toolContext == null ? Map.of() : toolContext.getContext();
        Object settings = context.get(SETTINGS_KEY);
        Object collector = context.get(COLLECTOR_KEY);
        if (!(settings instanceof WebSearchSettingsSnapshot settingsSnapshot)
                || !(collector instanceof ToolExecutionCollector toolExecutionCollector)) {
            throw new IllegalStateException("联网搜索工具缺少请求上下文");
        }
        return new WebSearchToolContext(settingsSnapshot, toolExecutionCollector);
    }
}
