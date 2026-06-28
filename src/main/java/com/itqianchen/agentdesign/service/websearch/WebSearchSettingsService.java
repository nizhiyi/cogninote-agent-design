package com.itqianchen.agentdesign.service.websearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchSettingsRequest;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchSettingsResponse;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchTestResponse;
import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;
import com.itqianchen.agentdesign.repository.settings.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 联网搜索全局设置服务。
 *
 * <p>设置存入 app_settings 的 JSON 快照；API Key 只在服务端保存和读取，响应永不回显明文。</p>
 */
@Service
public class WebSearchSettingsService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchSettingsService.class);
    private static final String SETTINGS_KEY = "web-search.settings";
    private static final WebSearchProvider DEFAULT_PROVIDER = WebSearchProvider.EXA;
    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int DEFAULT_MAX_CALLS_PER_TURN = 2;
    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final String DEFAULT_SEARCH_MODE = "auto";

    private final AppSettingRepository appSettingRepository;
    private final ObjectMapper objectMapper;
    private final WebSearchClient webSearchClient;

    public WebSearchSettingsService(
            AppSettingRepository appSettingRepository,
            ObjectMapper objectMapper,
            WebSearchClient webSearchClient
    ) {
        this.appSettingRepository = appSettingRepository;
        this.objectMapper = objectMapper;
        this.webSearchClient = webSearchClient;
    }

    @Transactional
    public WebSearchSettingsResponse settings() {
        return toResponse(snapshot());
    }

    @Transactional
    public WebSearchSettingsResponse update(WebSearchSettingsRequest request) {
        WebSearchSettingsSnapshot current = snapshot();
        String apiKey = request.apiKey() == null || request.apiKey().isBlank()
                ? current.apiKey()
                : request.apiKey().trim();
        WebSearchSettingsSnapshot updated = normalize(new StoredSettings(
                request.enabled() == null ? current.enabled() : request.enabled(),
                request.provider() == null ? current.provider() : request.provider(),
                apiKey,
                request.maxResults() == null ? current.maxResults() : request.maxResults(),
                request.maxCallsPerTurn() == null ? current.maxCallsPerTurn() : request.maxCallsPerTurn(),
                request.timeoutMs() == null ? current.timeoutMs() : request.timeoutMs(),
                request.searchMode() == null ? current.searchMode() : request.searchMode()
        ));
        appSettingRepository.save(SETTINGS_KEY, encode(updated));
        return toResponse(updated);
    }

    /**
     * 返回运行时设置快照。
     *
     * <p>工具挂载策略读取该方法；当用户未开启本轮联网时不应调用，避免无意义读取密钥。</p>
     */
    @Transactional
    public WebSearchSettingsSnapshot snapshot() {
        return appSettingRepository.findValue(SETTINGS_KEY)
                .map(this::decode)
                .map(WebSearchSettingsService::normalize)
                .orElseGet(this::initializeDefaults);
    }

    @Transactional
    public WebSearchTestResponse test() {
        WebSearchSettingsSnapshot settings = snapshot();
        if (!settings.enabled()) {
            return new WebSearchTestResponse(false, "联网搜索未启用。", 0);
        }
        if (!settings.apiKeyConfigured()) {
            return new WebSearchTestResponse(false, "Exa API Key 未配置。", 0);
        }
        WebSearchToolResult result = webSearchClient.search(
                new WebSearchRequest("latest AI search API news", settings.maxResults(), settings.searchMode(), true),
                settings
        );
        return new WebSearchTestResponse(result.success(), result.message(), result.results().size());
    }

    private WebSearchSettingsSnapshot initializeDefaults() {
        WebSearchSettingsSnapshot defaults = normalize(new StoredSettings(
                false,
                DEFAULT_PROVIDER,
                "",
                DEFAULT_MAX_RESULTS,
                DEFAULT_MAX_CALLS_PER_TURN,
                DEFAULT_TIMEOUT_MS,
                DEFAULT_SEARCH_MODE
        ));
        appSettingRepository.save(SETTINGS_KEY, encode(defaults));
        return defaults;
    }

    private String encode(WebSearchSettingsSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(new StoredSettings(
                    snapshot.enabled(),
                    snapshot.provider(),
                    snapshot.apiKey(),
                    snapshot.maxResults(),
                    snapshot.maxCallsPerTurn(),
                    snapshot.timeoutMs(),
                    snapshot.searchMode()
            ));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to encode web search settings", ex);
        }
    }

    private StoredSettings decode(String json) {
        try {
            return objectMapper.readValue(json, StoredSettings.class);
        } catch (JsonProcessingException ex) {
            log.warn("web_search_settings_decode_failed");
            return new StoredSettings(false, DEFAULT_PROVIDER, "", DEFAULT_MAX_RESULTS,
                    DEFAULT_MAX_CALLS_PER_TURN, DEFAULT_TIMEOUT_MS, DEFAULT_SEARCH_MODE);
        }
    }

    private static WebSearchSettingsSnapshot normalize(StoredSettings settings) {
        WebSearchProvider provider = settings.provider() == null ? DEFAULT_PROVIDER : settings.provider();
        String apiKey = settings.apiKey() == null ? "" : settings.apiKey().trim();
        int maxResults = Math.clamp(settings.maxResults(), 1, 10);
        int maxCallsPerTurn = Math.clamp(settings.maxCallsPerTurn(), 1, 3);
        int timeoutMs = Math.clamp(settings.timeoutMs(), 1000, 30_000);
        String searchMode = "fast".equalsIgnoreCase(settings.searchMode()) ? "fast" : DEFAULT_SEARCH_MODE;
        return new WebSearchSettingsSnapshot(
                settings.enabled() && !apiKey.isBlank(),
                provider,
                apiKey,
                maxResults,
                maxCallsPerTurn,
                timeoutMs,
                searchMode
        );
    }

    private static WebSearchSettingsResponse toResponse(WebSearchSettingsSnapshot snapshot) {
        return new WebSearchSettingsResponse(
                snapshot.enabled(),
                snapshot.provider(),
                snapshot.apiKeyConfigured(),
                snapshot.maxResults(),
                snapshot.maxCallsPerTurn(),
                snapshot.timeoutMs(),
                snapshot.searchMode()
        );
    }

    private record StoredSettings(
            boolean enabled,
            WebSearchProvider provider,
            String apiKey,
            int maxResults,
            int maxCallsPerTurn,
            int timeoutMs,
            String searchMode
    ) {
    }
}
