package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;

public final class OpenAiCompatibleUrls {

    private static final String MODELS_PATH = "/models";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";

    private OpenAiCompatibleUrls() {
    }

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

    public static URI modelsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + MODELS_PATH);
    }

    public static URI chatCompletionsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + CHAT_COMPLETIONS_PATH);
    }

    public static URI embeddingsUri(String baseUrl) {
        return URI.create(normalizeBaseUrl(baseUrl) + EMBEDDINGS_PATH);
    }

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

    private static void rejectQueryOrFragment(URI uri) {
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new ModelConfigurationException("Base URL must not contain query or fragment");
        }
    }

    private static String stripKnownEndpoint(String value) {
        for (String endpoint : new String[] {CHAT_COMPLETIONS_PATH, EMBEDDINGS_PATH, MODELS_PATH}) {
            if (value.endsWith(endpoint)) {
                return value.substring(0, value.length() - endpoint.length());
            }
        }
        return value;
    }

    private static String stripTrailingSlashes(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
