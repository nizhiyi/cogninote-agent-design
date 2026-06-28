package com.itqianchen.agentdesign.service.websearch;

/**
 * 联网搜索 Provider 客户端边界。
 *
 * <p>Agent 工具依赖该抽象，不直接感知 Exa 请求体和响应字段，后续替换国内 provider 时不改 Agent 主流程。</p>
 */
public interface WebSearchClient {

    /**
     * 执行一次只读公开网页搜索。
     *
     * @param request 归一化搜索请求
     * @param settings 本轮联网搜索设置快照
     * @return 可序列化的工具结果
     */
    WebSearchToolResult search(WebSearchRequest request, WebSearchSettingsSnapshot settings);
}
