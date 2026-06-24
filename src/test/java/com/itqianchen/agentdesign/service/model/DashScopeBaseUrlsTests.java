package com.itqianchen.agentdesign.service.model;


import com.itqianchen.agentdesign.domain.support.model.ModelConfigDefaults;
import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.support.model.ModelConfigDefaults;
import org.junit.jupiter.api.Test;

class DashScopeBaseUrlsTests {

    @Test
    void normalizeConfigBaseUrlConvertsOldCompatibleModeToNativeApiRoot() {
        assertThat(DashScopeBaseUrls.normalizeConfigBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/"))
                .isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    @Test
    void normalizeConfigBaseUrlConvertsDashScopeRootToNativeApiRoot() {
        assertThat(DashScopeBaseUrls.normalizeConfigBaseUrl("https://dashscope.aliyuncs.com/"))
                .isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    @Test
    void normalizeConfigBaseUrlAcceptsNativeApiRootFromDashScopeSdkExample() {
        assertThat(DashScopeBaseUrls.normalizeConfigBaseUrl("https://dashscope.aliyuncs.com/api/v1/"))
                .isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    @Test
    void modelsUriUsesCompatibleV1EndpointOnlyForModelCatalog() {
        assertThat(DashScopeBaseUrls.modelsUri("https://dashscope.aliyuncs.com/compatible-mode"))
                .hasToString("https://dashscope.aliyuncs.com/compatible-mode/v1/models");
    }

    @Test
    void modelsUriIgnoresCustomHostBecauseDashScopeProviderIsFixed() {
        assertThat(DashScopeBaseUrls.modelsUri("https://api.example.test/v1"))
                .hasToString("https://dashscope.aliyuncs.com/compatible-mode/v1/models");
    }

    @Test
    void toSpringAiAlibabaBaseUrlConvertsNativeApiRootForSpringAiAlibaba() {
        assertThat(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl(ModelConfigDefaults.BASE_URL))
                .isEqualTo("https://dashscope.aliyuncs.com");
        assertThat(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl("https://dashscope.aliyuncs.com/compatible-mode"))
                .isEqualTo("https://dashscope.aliyuncs.com");
    }

    @Test
    void toSpringAiAlibabaBaseUrlIgnoresCustomHostBecauseDashScopeProviderIsFixed() {
        assertThat(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl("https://api.example.test/v1"))
                .isEqualTo("https://dashscope.aliyuncs.com");
    }
}
