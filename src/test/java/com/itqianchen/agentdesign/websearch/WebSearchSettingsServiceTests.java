package com.itqianchen.agentdesign.websearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchSettingsRequest;
import com.itqianchen.agentdesign.domain.enums.websearch.WebSearchProvider;
import com.itqianchen.agentdesign.repository.settings.AppSettingRepository;
import com.itqianchen.agentdesign.service.websearch.WebSearchClient;
import com.itqianchen.agentdesign.service.websearch.WebSearchRequest;
import com.itqianchen.agentdesign.service.websearch.WebSearchSettingsService;
import com.itqianchen.agentdesign.service.websearch.WebSearchSettingsSnapshot;
import com.itqianchen.agentdesign.service.websearch.WebSearchToolResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebSearchSettingsServiceTests {

    @Test
    void settingsInitializesDisabledExaWithoutReturningApiKey() {
        WebSearchSettingsService service = new WebSearchSettingsService(
                new FakeAppSettingRepository(),
                new ObjectMapper(),
                new FakeWebSearchClient()
        );

        var settings = service.settings();

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.provider()).isEqualTo(WebSearchProvider.EXA);
        assertThat(settings.apiKeyConfigured()).isFalse();
    }

    @Test
    void blankApiKeyUpdatePreservesExistingSecret() {
        WebSearchSettingsService service = new WebSearchSettingsService(
                new FakeAppSettingRepository(),
                new ObjectMapper(),
                new FakeWebSearchClient()
        );

        service.update(new WebSearchSettingsRequest(true, WebSearchProvider.EXA, "sk-secret", 5, 2, 10000, "auto"));
        var response = service.update(new WebSearchSettingsRequest(true, WebSearchProvider.EXA, "", 6, 2, 10000, "fast"));

        assertThat(response.apiKeyConfigured()).isTrue();
        assertThat(service.snapshot().apiKey()).isEqualTo("sk-secret");
        assertThat(service.snapshot().maxResults()).isEqualTo(6);
        assertThat(service.snapshot().searchMode()).isEqualTo("fast");
    }

    @Test
    void updateCannotEnableWebSearchWithoutApiKey() {
        WebSearchSettingsService service = new WebSearchSettingsService(
                new FakeAppSettingRepository(),
                new ObjectMapper(),
                new FakeWebSearchClient()
        );

        var response = service.update(new WebSearchSettingsRequest(true, WebSearchProvider.EXA, "", 5, 2, 10000, "auto"));

        assertThat(response.enabled()).isFalse();
        assertThat(response.apiKeyConfigured()).isFalse();
        assertThat(service.snapshot().enabled()).isFalse();
    }

    /**
     * 测试用内存设置仓储。
     *
     * <p>覆盖真实 AppSettingRepository 的读写方法，避免设置服务测试依赖 MyBatis 和 SQLite。</p>
     */
    private static final class FakeAppSettingRepository extends AppSettingRepository {

        private final Map<String, String> values = new HashMap<>();

        private FakeAppSettingRepository() {
            super(null);
        }

        @Override
        public Optional<String> findValue(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void save(String key, String value) {
            values.put(key, value);
        }
    }

    private static final class FakeWebSearchClient implements WebSearchClient {

        @Override
        public WebSearchToolResult search(WebSearchRequest request, WebSearchSettingsSnapshot settings) {
            return WebSearchToolResult.success(java.util.List.of());
        }
    }
}
