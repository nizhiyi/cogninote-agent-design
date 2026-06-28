package com.itqianchen.agentdesign.service.websearch;

import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;
import com.itqianchen.agentdesign.domain.exception.websearch.WebSearchConfigurationException;
import com.itqianchen.agentdesign.domain.dto.chat.ChatToolEvent;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

/**
 * 联网搜索工具挂载策略。
 *
 * <p>所有 Agent 共用这一处判断，保证“用户本轮开关 + 全局设置 + API Key”三者缺一不可。</p>
 */
@Service
public class WebSearchToolPolicy {

    private final WebSearchSettingsService webSearchSettingsService;
    private final WebSearchTools webSearchTools;

    public WebSearchToolPolicy(WebSearchSettingsService webSearchSettingsService, WebSearchTools webSearchTools) {
        this.webSearchSettingsService = webSearchSettingsService;
        this.webSearchTools = webSearchTools;
    }

    /**
     * 准备本轮工具调用配置。
     *
     * <p>requestUseWebSearch 为 false 时直接返回 disabled，不读取设置和密钥，符合用户未启用联网的隐私预期。</p>
     */
    public WebSearchToolInvocation prepare(
            boolean requestUseWebSearch,
            String requestId,
            int initialSourceCount
    ) {
        if (!requestUseWebSearch) {
            return WebSearchToolInvocation.disabled();
        }

        WebSearchSettingsSnapshot settings = webSearchSettingsService.snapshot();
        if (!settings.enabled()) {
            throw new WebSearchConfigurationException("联网搜索未启用，请先在设置中开启联网搜索。");
        }
        if (settings.provider() != WebSearchProvider.EXA) {
            throw new WebSearchConfigurationException("当前仅支持 Exa 联网搜索。");
        }
        if (!settings.apiKeyConfigured()) {
            throw new WebSearchConfigurationException("Exa API Key 未配置，请先在联网搜索设置中保存 API Key。");
        }

        Sinks.Many<ChatToolEvent> sink = Sinks.many().multicast().onBackpressureBuffer();
        ToolExecutionCollector collector = new ToolExecutionCollector(
                requestId,
                settings.maxCallsPerTurn(),
                initialSourceCount,
                sink
        );
        return new WebSearchToolInvocation(
                List.of(webSearchTools),
                Map.of(
                        WebSearchToolContext.SETTINGS_KEY, settings,
                        WebSearchToolContext.COLLECTOR_KEY, collector
                ),
                collector,
                sink.asFlux()
        );
    }
}
