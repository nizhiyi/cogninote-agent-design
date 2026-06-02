package com.itqianchen.agentdesign.service.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashScopeModelFactoryTests {

    @Test
    void chatEndpointRoutesTraditionalQwenModelsToTextGeneration() {
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen-plus").multiModel())
                .isFalse();
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen-max").multiModel())
                .isFalse();
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3-max").multiModel())
                .isFalse();
    }

    @Test
    void chatEndpointRoutesNewDashScopeMultimodalModelsToMultimodalGeneration() {
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3.6-plus").multiModel())
                .isTrue();
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3.7-plus").multiModel())
                .isTrue();
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3.5-omni-plus").multiModel())
                .isTrue();
        assertThat(DashScopeModelFactory.DashScopeChatEndpoint.fromModel("qwen3-vl-plus").multiModel())
                .isTrue();
    }
}
