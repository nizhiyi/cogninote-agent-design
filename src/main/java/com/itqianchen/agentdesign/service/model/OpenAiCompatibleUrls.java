package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Open Ai Compatible Urls 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
public final class OpenAiCompatibleUrls {

    private static final String MODELS_PATH = "/models";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    /**
     * 注入 OpenAiCompatibleUrls 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    private OpenAiCompatibleUrls() {
    }

    /**
     * 规范化 normalize Base Url 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ModelConfigurationException("Base URL is required for OpenAI-compatible provider");
        }

        URI uri = parseHttpUri(stripTrailingSlashes(baseUrl.trim()), baseUrl);
        /**
         * 执行 模型配置 中的 reject Query Or Fragment 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        rejectQueryOrFragment(uri);

        String value = uri.toString();
        // 用户很容易从文档里复制完整接口地址。
        // 配置语义是“Base URL”，所以这里去掉已知 endpoint，避免后续拼出重复路径。
        return stripKnownEndpoint(value);
    }

    /**
     * 执行 模型配置 中的 models Uri 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public static URI modelsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + MODELS_PATH);
    }

    /**
     * 执行 模型配置 中的 chat Completions Uri 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public static URI chatCompletionsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + CHAT_COMPLETIONS_PATH);
    }

    /**
     * 执行 模型配置 中的 embeddings Uri 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public static URI embeddingsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + EMBEDDINGS_PATH);
    }

    /**
     * 解析 parse Http Uri 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    private static URI parseHttpUri(String value, String originalValue) {
        try {
            URI uri = new URI(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new ModelConfigurationException("Base URL must use http or https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ModelConfigurationException("Base URL host is required");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new ModelConfigurationException("Base URL is not valid: " + originalValue, ex);
        }
    }

    /**
     * 执行 模型配置 中的 reject Query Or Fragment 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static void rejectQueryOrFragment(URI uri) {
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new ModelConfigurationException("Base URL must not contain query or fragment");
        }
    }

    /**
     * 执行 模型配置 中的 strip Known Endpoint 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String stripKnownEndpoint(String value) {
        for (String endpoint : new String[] {CHAT_COMPLETIONS_PATH, EMBEDDINGS_PATH, MODELS_PATH}) {
            if (value.endsWith(endpoint)) {
                return value.substring(0, value.length() - endpoint.length());
            }
        }
        return value;
    }

    /**
     * 执行 模型配置 中的 strip Trailing Slashes 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String stripTrailingSlashes(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
