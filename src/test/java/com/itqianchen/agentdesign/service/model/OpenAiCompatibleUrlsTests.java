package com.itqianchen.agentdesign.service.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiCompatibleUrlsTests {

    @Test
    void normalizeBaseUrlKeepsCustomProviderBaseUrl() {
        assertThat(OpenAiCompatibleUrls.normalizeBaseUrl("https://api.example.test/v1/"))
                .isEqualTo("https://api.example.test/v1");
    }

    @Test
    void normalizeBaseUrlStripsCopiedEndpointPath() {
        assertThat(OpenAiCompatibleUrls.normalizeBaseUrl("https://api.example.test/v1/chat/completions"))
                .isEqualTo("https://api.example.test/v1");
        assertThat(OpenAiCompatibleUrls.normalizeBaseUrl("https://api.example.test/v1/embeddings"))
                .isEqualTo("https://api.example.test/v1");
    }

    @Test
    void endpointsAreBuiltFromCustomBaseUrl() {
        assertThat(OpenAiCompatibleUrls.modelsUri("https://api.example.test/v1"))
                .hasToString("https://api.example.test/v1/models");
        assertThat(OpenAiCompatibleUrls.chatCompletionsUri("https://api.example.test/v1"))
                .hasToString("https://api.example.test/v1/chat/completions");
        assertThat(OpenAiCompatibleUrls.embeddingsUri("https://api.example.test/v1"))
                .hasToString("https://api.example.test/v1/embeddings");
    }
}
