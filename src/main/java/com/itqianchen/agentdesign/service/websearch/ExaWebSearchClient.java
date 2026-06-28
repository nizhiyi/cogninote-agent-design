package com.itqianchen.agentdesign.service.websearch;

import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Exa 搜索客户端。
 *
 * <p>第 35 阶段固定调用官方 search endpoint，只请求 highlights，不拉全文，控制 token 和费用。</p>
 */
@Component
public class ExaWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(ExaWebSearchClient.class);
    private static final URI SEARCH_URI = URI.create("https://api.exa.ai/search");
    private static final int MIN_TIMEOUT_MS = 1_000;
    private static final int MAX_TIMEOUT_MS = 30_000;

    private final ConcurrentMap<Integer, RestClient> restClientsByTimeoutMs = new ConcurrentHashMap<>();

    @Override
    public WebSearchToolResult search(WebSearchRequest request, WebSearchSettingsSnapshot settings) {
        if (settings.provider() != WebSearchProvider.EXA) {
            return WebSearchToolResult.failure("暂不支持的联网搜索 Provider: " + settings.provider());
        }
        if (!settings.apiKeyConfigured()) {
            return WebSearchToolResult.failure("Exa API Key 未配置。");
        }

        try {
            ExaSearchResponse response = restClient(settings.timeoutMs())
                    .post()
                    .uri(SEARCH_URI)
                    .header("x-api-key", settings.apiKey())
                    .body(ExaSearchRequest.from(request))
                    .retrieve()
                    .body(ExaSearchResponse.class);
            return WebSearchToolResult.success(toResultItems(response));
        } catch (RestClientException ex) {
            log.warn("web_search_provider_failed provider={} errorType={}",
                    settings.provider(),
                    ex.getClass().getSimpleName()
            );
            return WebSearchToolResult.failure("联网搜索调用失败，请检查 API Key、额度或网络连接。");
        }
    }

    private RestClient restClient(int timeoutMs) {
        int normalizedTimeoutMs = Math.clamp(timeoutMs, MIN_TIMEOUT_MS, MAX_TIMEOUT_MS);
        return restClientsByTimeoutMs.computeIfAbsent(normalizedTimeoutMs, ExaWebSearchClient::buildRestClient);
    }

    private static RestClient buildRestClient(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofMillis(timeoutMs);
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private static List<WebSearchResultItem> toResultItems(ExaSearchResponse response) {
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .filter(result -> result.url() != null && !result.url().isBlank())
                .map(result -> new WebSearchResultItem(
                        result.title(),
                        result.url(),
                        snippet(result),
                        WebSearchProvider.EXA.name(),
                        result.score() == null ? 0.0 : result.score(),
                        result.publishedDate()
                ))
                .toList();
    }

    private static String snippet(ExaResult result) {
        if (result.highlights() == null || result.highlights().isEmpty()) {
            return "";
        }
        return String.join("\n", result.highlights()).trim();
    }

    private record ExaSearchRequest(
            String query,
            String type,
            int numResults,
            ExaContents contents
    ) {
        static ExaSearchRequest from(WebSearchRequest request) {
            return new ExaSearchRequest(
                    request.query(),
                    normalizeMode(request.searchMode()),
                    request.maxResults(),
                    new ExaContents(request.includeHighlights())
            );
        }
    }

    private record ExaContents(boolean highlights) {
    }

    private record ExaSearchResponse(List<ExaResult> results) {
    }

    private record ExaResult(
            String title,
            String url,
            Double score,
            String publishedDate,
            List<String> highlights
    ) {
    }

    private static String normalizeMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase();
        return "fast".equals(normalized) ? "fast" : "auto";
    }
}
