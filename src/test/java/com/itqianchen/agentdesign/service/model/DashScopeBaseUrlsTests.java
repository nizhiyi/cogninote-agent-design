package com.itqianchen.agentdesign.service.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import org.junit.jupiter.api.Test;

/**
 * Dash Scope Base Urls 测试 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class DashScopeBaseUrlsTests {

    /**
     * 规范化 normalize 配置 Base Url Converts Old Compatible Mode To Native Api Root 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    @Test
    void normalizeConfigBaseUrlConvertsOldCompatibleModeToNativeApiRoot() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.normalizeConfigBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/"))
                .isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    /**
     * 规范化 normalize 配置 Base Url Converts Dash Scope Root To Native Api Root 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    @Test
    void normalizeConfigBaseUrlConvertsDashScopeRootToNativeApiRoot() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.normalizeConfigBaseUrl("https://dashscope.aliyuncs.com/"))
                .isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    /**
     * 规范化 normalize 配置 Base Url Accepts Native Api Root From Dash Scope Sdk Example 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    @Test
    void normalizeConfigBaseUrlAcceptsNativeApiRootFromDashScopeSdkExample() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.normalizeConfigBaseUrl("https://dashscope.aliyuncs.com/api/v1/"))
                .isEqualTo(ModelConfigDefaults.BASE_URL);
    }

    /**
     * 执行 模型配置 中的 models Uri Uses Compatible V1 Endpoint Only For Model Catalog 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void modelsUriUsesCompatibleV1EndpointOnlyForModelCatalog() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.modelsUri("https://dashscope.aliyuncs.com/compatible-mode"))
                .hasToString("https://dashscope.aliyuncs.com/compatible-mode/v1/models");
    }

    /**
     * 执行 模型配置 中的 models Uri Ignores Custom Host Because Dash Scope Provider Is Fixed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void modelsUriIgnoresCustomHostBecauseDashScopeProviderIsFixed() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.modelsUri("https://api.example.test/v1"))
                .hasToString("https://dashscope.aliyuncs.com/compatible-mode/v1/models");
    }

    /**
     * 执行 模型配置 中的 to Spring Ai Alibaba Base Url Converts Native Api Root For Spring Ai Alibaba 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void toSpringAiAlibabaBaseUrlConvertsNativeApiRootForSpringAiAlibaba() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl(ModelConfigDefaults.BASE_URL))
                .isEqualTo("https://dashscope.aliyuncs.com");
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl("https://dashscope.aliyuncs.com/compatible-mode"))
                .isEqualTo("https://dashscope.aliyuncs.com");
    }

    /**
     * 执行 模型配置 中的 to Spring Ai Alibaba Base Url Ignores Custom Host Because Dash Scope Provider Is Fixed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void toSpringAiAlibabaBaseUrlIgnoresCustomHostBecauseDashScopeProviderIsFixed() {
        /**
         * 执行 模型配置 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(DashScopeBaseUrls.toSpringAiAlibabaBaseUrl("https://api.example.test/v1"))
                .isEqualTo("https://dashscope.aliyuncs.com");
    }
}
