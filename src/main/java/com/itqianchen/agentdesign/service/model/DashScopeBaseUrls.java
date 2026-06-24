package com.itqianchen.agentdesign.service.model;


import com.itqianchen.agentdesign.domain.exception.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.support.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.support.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.exception.model.ModelConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;

final class DashScopeBaseUrls {

    private static final String MODELS_PATH = "/models";
    private static final String NATIVE_API_V1_PATH = "/api/v1";
    private static final String COMPATIBLE_PATH = "/compatible-mode";
    private static final String COMPATIBLE_V1_PATH = "/compatible-mode/v1";
    private static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";
    private static final String DASHSCOPE_ORIGIN = "https://dashscope.aliyuncs.com";
    private static final String COMPATIBLE_MODE_MODELS_BASE_URL = DASHSCOPE_ORIGIN + COMPATIBLE_V1_PATH;

    /**
     * 工具类禁止实例化。
     */
    private DashScopeBaseUrls() {
    }

    /**
     * 归一化 DashScope Provider 的配置地址。
     *
     * @param baseUrl 用户保存的 Base URL
     * @return DashScope 原生 API Root
     */
    static String normalizeConfigBaseUrl(String baseUrl) {
        String candidate = baseUrl == null || baseUrl.isBlank()
                ? ModelConfigDefaults.BASE_URL
                : baseUrl.trim();
        URI uri = parseHttpUri(stripTrailingSlashes(candidate), baseUrl);
        rejectQueryOrFragment(uri);

        String normalized = uri.toString();
        String path = normalizedPath(uri);
        if (!DASHSCOPE_HOST.equalsIgnoreCase(uri.getHost())) {
            // DashScope Provider 不承载自定义 host；旧配置里若误存了自定义地址，
            // 这里直接收敛回百炼默认地址，避免继续喂给 Spring AI Alibaba。
            return ModelConfigDefaults.BASE_URL;
        }
        if (path.isBlank() && DASHSCOPE_HOST.equalsIgnoreCase(uri.getHost())) {
            return origin(uri) + NATIVE_API_V1_PATH;
        }
        if (NATIVE_API_V1_PATH.equals(path)) {
            return normalized;
        }
        if (COMPATIBLE_PATH.equals(path) || COMPATIBLE_V1_PATH.equals(path)) {
            // 旧版本曾把 DashScope Provider 的配置地址保存成 OpenAI-compatible 地址。
            // 这里迁回原生 API Root，避免 UI 和后端 Provider 语义继续混在一起。
            return origin(uri) + NATIVE_API_V1_PATH;
        }
        return normalized;
    }

    /**
     * 返回 DashScope 模型列表接口地址。
     *
     * @param configuredBaseUrl 用户配置地址；DashScope Provider 固定使用百炼兼容模型列表
     * @return 模型列表 URI
     */
    static URI modelsUri(String configuredBaseUrl) {
        // DashScope 是固定百炼通道，不读取用户自定义 host。
        // 需要自定义 Base URL 时应切到 OPENAI_COMPATIBLE provider。
        return URI.create(COMPATIBLE_MODE_MODELS_BASE_URL + MODELS_PATH);
    }

    /**
     * 转换为 Spring AI Alibaba DashScopeApi 需要的 baseUrl。
     *
     * @param configuredBaseUrl 用户配置地址
     * @return DashScope 裸域名
     */
    static String toSpringAiAlibabaBaseUrl(String configuredBaseUrl) {
        // DashScope SDK 示例里的 base_http_api_url 是 https://dashscope.aliyuncs.com/api/v1。
        // 但 Spring AI Alibaba 的 DashScopeApi.Builder 默认 path 已包含 /api/v1/services/...，
        // 因此这里必须把用户可见的 API Root 转成 Builder 需要的裸域名。
        URI uri = parseHttpUri(normalizeConfigBaseUrl(configuredBaseUrl), configuredBaseUrl);
        String path = normalizedPath(uri);
        if (path.isBlank()) {
            return uri.toString();
        }
        if (NATIVE_API_V1_PATH.equals(path) || COMPATIBLE_PATH.equals(path) || COMPATIBLE_V1_PATH.equals(path)) {
            return origin(uri);
        }
        throw new ModelConfigurationException(
                "当前 Provider=DASHSCOPE 只支持 DashScope 原生 API Root");
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

    /**
     * 读取并归一化 URI path。
     *
     * @param uri URI
     * @return 无尾部斜杠 path；根路径返回空串
     */
    private static String normalizedPath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        return stripTrailingSlashes(path);
    }

    /**
     * 组合 URI origin。
     *
     * @param uri URI
     * @return scheme + authority
     */
    private static String origin(URI uri) {
        return new StringBuilder()
                .append(uri.getScheme())
                .append("://")
                .append(uri.getAuthority())
                .toString();
    }
}
