package com.itqianchen.agentdesign.service.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Open Ai Compatible Urls 测试 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class OpenAiCompatibleUrlsTests {

    /**
     * 规范化 normalize Base Url Keeps Custom Provider Base Url 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    @Test
    void normalizeBaseUrlKeepsCustomProviderBaseUrl() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(OpenAiCompatibleUrls.normalizeBaseUrl("https://api.example.test/v1/"))
                .isEqualTo("https://api.example.test/v1");
    }

    /**
     * 规范化 normalize Base Url Strips Copied Endpoint Path 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    @Test
    void normalizeBaseUrlStripsCopiedEndpointPath() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(OpenAiCompatibleUrls.normalizeBaseUrl("https://api.example.test/v1/chat/completions"))
                .isEqualTo("https://api.example.test/v1");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(OpenAiCompatibleUrls.normalizeBaseUrl("https://api.example.test/v1/embeddings"))
                .isEqualTo("https://api.example.test/v1");
    }

    /**
     * 执行 模型配置 中的 endpoints Are Built From Custom Base Url 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void endpointsAreBuiltFromCustomBaseUrl() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(OpenAiCompatibleUrls.modelsUri("https://api.example.test/v1"))
                .hasToString("https://api.example.test/v1/models");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(OpenAiCompatibleUrls.chatCompletionsUri("https://api.example.test/v1"))
                .hasToString("https://api.example.test/v1/chat/completions");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(OpenAiCompatibleUrls.embeddingsUri("https://api.example.test/v1"))
                .hasToString("https://api.example.test/v1/embeddings");
    }
}
