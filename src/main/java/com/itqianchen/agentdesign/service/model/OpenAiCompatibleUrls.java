package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;

public final class OpenAiCompatibleUrls {

    private static final String MODELS_PATH = "/models";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    /**
     * 工具类禁止实例化。
     */
    private OpenAiCompatibleUrls() {
    }

    /**
     * 归一化 OpenAI-compatible Base URL。
     *
     * @param baseUrl 用户输入地址
     * @return 不带已知 endpoint 的 Base URL
     */
    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ModelConfigurationException("Base URL is required for OpenAI-compatible provider");
        }

        URI uri = parseHttpUri(stripTrailingSlashes(baseUrl.trim()), baseUrl);
        rejectQueryOrFragment(uri);

        String value = uri.toString();
        // 用户很容易从文档里复制完整接口地址。
        // 配置语义是“Base URL”，所以这里去掉已知 endpoint，避免后续拼出重复路径。
        return stripKnownEndpoint(value);
    }

    /**
     * 组合模型列表接口地址。
     *
     * @param baseUrl Base URL
     * @return /models URI
     */
    public static URI modelsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + MODELS_PATH);
    }

    /**
     * 组合 Chat Completions 接口地址。
     *
     * @param baseUrl Base URL
     * @return /chat/completions URI
     */
    public static URI chatCompletionsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + CHAT_COMPLETIONS_PATH);
    }

    /**
     * 组合 Embeddings 接口地址。
     *
     * @param baseUrl Base URL
     * @return /embeddings URI
     */
    public static URI embeddingsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + EMBEDDINGS_PATH);
    }

    /**
     * 只接受 http/https 且必须带 host 的 Base URL。
     *
     * @param value 已清理尾部斜杠的地址
     * @param originalValue 用户原始输入，用于错误提示
     * @return URI
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
     * 拒绝带 query 或 fragment 的 Base URL。
     *
     * @param uri 待校验 URI
     */
    private static void rejectQueryOrFragment(URI uri) {
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new ModelConfigurationException("Base URL must not contain query or fragment");
        }
    }

    /**
     * 移除用户误复制的标准 endpoint。
     *
     * @param value 归一化后的 URI 字符串
     * @return Base URL
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
     * 移除末尾斜杠。
     *
     * @param value 原始地址
     * @return 无尾部斜杠的地址
     */
    private static String stripTrailingSlashes(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
