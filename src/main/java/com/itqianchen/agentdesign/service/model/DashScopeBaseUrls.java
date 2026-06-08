package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Dash Scope Base Urls 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
final class DashScopeBaseUrls {

    private static final String MODELS_PATH = "/models";
    private static final String NATIVE_API_V1_PATH = "/api/v1";
    private static final String COMPATIBLE_PATH = "/compatible-mode";
    private static final String COMPATIBLE_V1_PATH = "/compatible-mode/v1";
    private static final String DASHSCOPE_HOST = "dashscope.aliyuncs.com";
    private static final String DASHSCOPE_ORIGIN = "https://dashscope.aliyuncs.com";
    private static final String COMPATIBLE_MODE_MODELS_BASE_URL = DASHSCOPE_ORIGIN + COMPATIBLE_V1_PATH;

    /**
     * 注入 DashScopeBaseUrls 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    private DashScopeBaseUrls() {
    }

    /**
     * 规范化 normalize 配置 Base Url 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    static String normalizeConfigBaseUrl(String baseUrl) {
        String candidate = baseUrl == null || baseUrl.isBlank()
                ? ModelConfigDefaults.BASE_URL
                : baseUrl.trim();
        URI uri = parseHttpUri(stripTrailingSlashes(candidate), baseUrl);
        /**
         * 执行 模型配置 中的 reject Query Or Fragment 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
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
     * 执行 模型配置 中的 models Uri 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    static URI modelsUri(String configuredBaseUrl) {
        // DashScope 是固定百炼通道，不读取用户自定义 host。
        // 需要自定义 Base URL 时应切到 OPENAI_COMPATIBLE provider。
        return URI.create(COMPATIBLE_MODE_MODELS_BASE_URL + MODELS_PATH);
    }

    /**
     * 执行 模型配置 中的 to Spring Ai Alibaba Base Url 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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

    /**
     * 规范化 normalized Path 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static String normalizedPath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        return stripTrailingSlashes(path);
    }

    /**
     * 执行 模型配置 中的 origin 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String origin(URI uri) {
        return new StringBuilder()
                .append(uri.getScheme())
                .append("://")
                .append(uri.getAuthority())
                .toString();
    }
}
