package com.itqianchen.agentdesign.service.websearch;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 暴露给模型的联网搜索工具。
 *
 * <p>Bean 本身不保存请求状态；每轮的设置、调用次数和来源收集器都从 ToolContext 读取。</p>
 */
@Component
public class WebSearchTools {

    private final WebSearchClient webSearchClient;

    public WebSearchTools(WebSearchClient webSearchClient) {
        this.webSearchClient = webSearchClient;
    }

    /**
     * 搜索公开网页信息。
     *
     * @param query 简短搜索词，不能包含 API Key、Token、密码或用户隐私数据
     * @param toolContext Spring AI 注入的工具上下文
     * @return 可返回给模型的搜索结果
     */
    @Tool(
            name = "searchWeb",
            description = """
                    搜索公开网页，获取最新、外部或可核验的信息。
                    仅当回答需要实时事实、公开网页证据，或当前上下文与已提供资料不足以可靠回答时使用。
                    """
    )
    public WebSearchToolResult searchWeb(
            @ToolParam(description = "简洁明确的搜索关键词。不要包含 API Key、Token、密码或用户隐私数据。")
            String query,
            ToolContext toolContext
    ) {
        long startedAt = System.currentTimeMillis();
        WebSearchToolContext context = WebSearchToolContext.from(toolContext);
        String normalizedQuery = WebSearchQuerySanitizer.normalize(query);
        if (normalizedQuery.isBlank()) {
            WebSearchToolResult result = WebSearchToolResult.failure("搜索 query 为空，未调用联网搜索。");
            context.collector().record("searchWeb", "", result, System.currentTimeMillis() - startedAt);
            return result;
        }

        try {
            context.collector().checkCallLimit();
            WebSearchRequest request = new WebSearchRequest(
                    normalizedQuery,
                    context.settings().maxResults(),
                    context.settings().searchMode(),
                    true
            );
            WebSearchToolResult result = webSearchClient.search(request, context.settings());
            context.collector().record("searchWeb", normalizedQuery, result, System.currentTimeMillis() - startedAt);
            return result;
        } catch (RuntimeException ex) {
            WebSearchToolResult result = WebSearchToolResult.failure(ex.getMessage());
            context.collector().record("searchWeb", normalizedQuery, result, System.currentTimeMillis() - startedAt);
            return result;
        }
    }
}
