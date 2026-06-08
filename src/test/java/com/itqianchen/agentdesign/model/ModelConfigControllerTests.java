package com.itqianchen.agentdesign.model;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itqianchen.agentdesign.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Model 配置 控制器 测试 承担 模型配置 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-model-controller",
        "app.storage.database-path=target/test-cogninote-model-controller/cogninote.db",
        "server.address=127.0.0.1"
})
class ModelConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDatabaseCleaner databaseCleaner;

    /**
     * 清理 clear Database 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @BeforeEach
    void clearDatabase() {
        databaseCleaner.clearModelConfigs();
    }

    /**
     * 拉取 fetch Models Requires Api Key And Returns Unified Error 数据。
     * <p>外部 HTTP 或模型提供商响应会在这里转换为本地 DTO。</p>
     */
    @Test
    void fetchModelsRequiresApiKeyAndReturnsUnifiedError() throws Exception {
        mockMvc.perform(post("/api/model-config/models")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "CHAT",
                                  "provider": "DASHSCOPE",
                                  "displayName": "DashScope",
                                  "baseUrl": "https://dashscope.aliyuncs.com/api/v1",
                                  "apiKey": "",
                                  "modelName": "qwen-plus",
                                  "chatModel": "qwen-plus",
                                  "embeddingModel": "text-embedding-v4",
                                  "embeddingDimensions": 1024,
                                  "temperature": 0.7,
                                  "topK": 8,
                                  "defaultTopK": 8
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MODEL_CONFIGURATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("API Key")));
    }

    /**
     * 执行 模型配置 中的 active Configs Returns Split Model Configs 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void activeConfigsReturnsSplitModelConfigs() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/model-configs/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chat.role").value("CHAT"))
                .andExpect(jsonPath("$.data.embedding.role").value("EMBEDDING"));
    }

    /**
     * 设置 settings Snapshot Returns Active Summary And Selected 配置 状态。
     * <p>状态变更会同步维护当前模块需要的派生信息。</p>
     */
    @Test
    void settingsSnapshotReturnsActiveSummaryAndSelectedConfig() throws Exception {
        mockMvc.perform(get("/api/model-configs/settings").param("role", "CHAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("CHAT"))
                .andExpect(jsonPath("$.data.active.chat.role").value("CHAT"))
                .andExpect(jsonPath("$.data.active.embedding.role").value("EMBEDDING"))
                .andExpect(jsonPath("$.data.selectedConfig.role").value("CHAT"));
    }

    /**
     * 设置 settings Create Returns Created 配置 As Selected 状态。
     * <p>状态变更会同步维护当前模块需要的派生信息。</p>
     */
    @Test
    void settingsCreateReturnsCreatedConfigAsSelected() throws Exception {
        mockMvc.perform(post("/api/model-configs/settings/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "CHAT",
                                  "provider": "OPENAI_COMPATIBLE",
                                  "displayName": "Chat Settings",
                                  "baseUrl": "https://api.example.test/v1",
                                  "apiKey": "sk-test",
                                  "modelName": "gpt-test",
                                  "temperature": 0.4,
                                  "defaultTopK": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("CHAT"))
                .andExpect(jsonPath("$.data.selectedConfig.displayName").value("Chat Settings"))
                .andExpect(jsonPath("$.data.selectedConfig.temperature").value(0.4))
                .andExpect(jsonPath("$.data.selectedConfig.defaultTopK").value(6));
    }

    /**
     * 设置 settings Create And Snapshot Force Open Ai Compatible Embedding Dimensions To1024 状态。
     * <p>状态变更会同步维护当前模块需要的派生信息。</p>
     */
    @Test
    void settingsCreateAndSnapshotForceOpenAiCompatibleEmbeddingDimensionsTo1024() throws Exception {
        mockMvc.perform(post("/api/model-configs/settings/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "EMBEDDING",
                                  "provider": "OPENAI_COMPATIBLE",
                                  "displayName": "Qwen Embedding",
                                  "baseUrl": "https://api.siliconflow.cn/v1",
                                  "apiKey": "sk-test",
                                  "modelName": "Qwen/Qwen3-Embedding-8B",
                                  "embeddingDimensions": 2048
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selectedConfig.provider").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.data.selectedConfig.embeddingDimensions").value(1024));

        mockMvc.perform(get("/api/model-configs/settings").param("role", "EMBEDDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selectedConfig.provider").value("OPENAI_COMPATIBLE"))
                .andExpect(jsonPath("$.data.selectedConfig.embeddingDimensions").value(1024));
    }

    /**
     * 设置 settings Delete Only 配置 Returns Fallback Selected 配置 状态。
     * <p>状态变更会同步维护当前模块需要的派生信息。</p>
     */
    @Test
    void settingsDeleteOnlyConfigReturnsFallbackSelectedConfig() throws Exception {
        String body = mockMvc.perform(post("/api/model-configs/settings/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "EMBEDDING",
                                  "provider": "DASHSCOPE",
                                  "displayName": "Embedding Settings",
                                  "baseUrl": "https://dashscope.aliyuncs.com/api/v1",
                                  "apiKey": "",
                                  "modelName": "text-embedding-v4",
                                  "embeddingDimensions": 1024
                                }
                                """))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.data.selectedConfig.id");

        mockMvc.perform(delete("/api/model-configs/settings/configs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("EMBEDDING"))
                .andExpect(jsonPath("$.data.selectedConfig.role").value("EMBEDDING"))
                .andExpect(jsonPath("$.data.selectedConfig.active").value(true));
    }

    /**
     * 查询 模型配置 列表。
     * <p>返回值面向上层展示或接口响应，不暴露底层存储细节。</p>
     */
    @Test
    void listConfigsRejectsInvalidRoleWithUnifiedBadRequest() throws Exception {
        mockMvc.perform(get("/api/model-configs").param("role", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MODEL_CONFIGURATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Invalid role")));
    }

    /**
     * 执行 模型配置 中的 embedding Connection 测试 Returns Explicit Skip Message 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void embeddingConnectionTestReturnsExplicitSkipMessage() throws Exception {
        mockMvc.perform(post("/api/model-configs/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "EMBEDDING",
                                  "provider": "DASHSCOPE",
                                  "displayName": "DashScope Embedding",
                                  "baseUrl": "https://dashscope.aliyuncs.com/api/v1",
                                  "apiKey": "sk-test",
                                  "modelName": "text-embedding-v4",
                                  "embeddingDimensions": 1024
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("未发起向量调用")));
    }
}
