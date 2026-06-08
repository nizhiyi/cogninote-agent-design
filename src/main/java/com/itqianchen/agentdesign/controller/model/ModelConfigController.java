package com.itqianchen.agentdesign.controller.model;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.dto.model.ActiveModelConfigsResponse;
import com.itqianchen.agentdesign.dto.model.LegacyModelConfigResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.dto.model.ModelConfigResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigSettingsResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigTestResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigUpsertRequest;
import com.itqianchen.agentdesign.dto.model.ModelOptionsResponse;
import com.itqianchen.agentdesign.service.model.ModelCatalogService;
import com.itqianchen.agentdesign.service.model.ModelConnectionTestService;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ModelConfigController 暴露模型配置相关 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final ModelCatalogService modelCatalogService;
    private final ModelConnectionTestService modelConnectionTestService;

    /**
     * 注入 ModelConfigController 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ModelConfigController(
            ModelConfigService modelConfigService,
            ModelCatalogService modelCatalogService,
            ModelConnectionTestService modelConnectionTestService
    ) {
        this.modelConfigService = modelConfigService;
        this.modelCatalogService = modelCatalogService;
        this.modelConnectionTestService = modelConnectionTestService;
    }

    /**
     * 查询 模型配置 列表。
     * <p>返回值面向上层展示或接口响应，不暴露底层存储细节。</p>
     */
    @GetMapping("/api/model-configs")
    public ApiResponse<List<ModelConfigResponse>> listConfigs(@RequestParam String role) {
        return ApiResponse.ok(modelConfigService.list(parseRole(role)).stream()
                .map(ModelConfigResponse::from)
                .toList());
    }

    /**
     * 返回当前激活的对话模型和向量模型配置。
     * <p>该接口服务于前端顶部状态和运行前置检查。</p>
     */
    @GetMapping("/api/model-configs/active")
    public ApiResponse<ActiveModelConfigsResponse> activeConfigs() {
        return ApiResponse.ok(new ActiveModelConfigsResponse(
                ModelConfigResponse.from(modelConfigService.activeChatOrDefault()),
                ModelConfigResponse.from(modelConfigService.activeEmbeddingOrDefault())
        ));
    }

    /**
     * 构建模型设置页使用的配置快照。
     * <p>快照同时包含列表、选中项和当前激活项，便于前端一次性渲染。</p>
     */
    @GetMapping("/api/model-configs/settings")
    public ApiResponse<ModelConfigSettingsResponse> settingsSnapshot(@RequestParam String role) {
        return ApiResponse.ok(modelConfigService.settingsSnapshot(parseRole(role)));
    }

    /**
     * 创建 create Settings 配置 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @PostMapping("/api/model-configs/settings/configs")
    public ApiResponse<ModelConfigSettingsResponse> createSettingsConfig(
            @Valid @RequestBody ModelConfigUpsertRequest request
    ) {
        return ApiResponse.ok(modelConfigService.createSettings(request));
    }

    /**
     * 更新 update Settings 配置 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @PutMapping("/api/model-configs/settings/configs/{id}")
    public ApiResponse<ModelConfigSettingsResponse> updateSettingsConfig(
            @PathVariable String id,
            @Valid @RequestBody ModelConfigUpsertRequest request
    ) {
        return ApiResponse.ok(modelConfigService.updateSettings(id, request));
    }

    /**
     * 删除 delete Settings 配置 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @DeleteMapping("/api/model-configs/settings/configs/{id}")
    public ApiResponse<ModelConfigSettingsResponse> deleteSettingsConfig(@PathVariable String id) {
        return ApiResponse.ok(modelConfigService.deleteSettings(id));
    }

    /**
     * 执行 模型配置 中的 activate Settings 配置 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @PostMapping("/api/model-configs/settings/configs/{id}/activate")
    public ApiResponse<ModelConfigSettingsResponse> activateSettingsConfig(@PathVariable String id) {
        return ApiResponse.ok(modelConfigService.activateSettings(id));
    }

    /**
     * 创建 create 配置 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @PostMapping("/api/model-configs")
    public ApiResponse<ModelConfigResponse> createConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.create(request)));
    }

    /**
     * 更新 update 配置 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @PutMapping("/api/model-configs/{id}")
    public ApiResponse<ModelConfigResponse> updateConfig(
            @PathVariable String id,
            @Valid @RequestBody ModelConfigRequest request
    ) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.update(id, request)));
    }

    /**
     * 删除 delete 配置 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @DeleteMapping("/api/model-configs/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable String id) {
        modelConfigService.delete(id);
        return ApiResponse.ok(null);
    }

    /**
     * 执行 模型配置 中的 activate 配置 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @PostMapping("/api/model-configs/{id}/activate")
    public ApiResponse<ModelConfigResponse> activateConfig(@PathVariable String id) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.activate(id)));
    }

    /**
     * 测试 test Role 配置 是否可用。
     * <p>使用最小请求验证配置、网络和模型服务是否连通。</p>
     */
    @PostMapping("/api/model-configs/test")
    public ApiResponse<ModelConfigTestResponse> testRoleConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(modelConnectionTestService.test(modelConfigService.connectionTestConfig(request)));
    }

    /**
     * 拉取 fetch Role Models 数据。
     * <p>外部 HTTP 或模型提供商响应会在这里转换为本地 DTO。</p>
     */
    @PostMapping("/api/model-configs/models")
    public ApiResponse<ModelOptionsResponse> fetchRoleModels(@Valid @RequestBody ModelConfigRequest request) {
        // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
        return ApiResponse.ok(modelCatalogService.fetchModels(request));
    }

    /**
     * 读取 get 配置 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    @GetMapping("/api/model-config")
    public ApiResponse<LegacyModelConfigResponse> getConfig() {
        return ApiResponse.ok(LegacyModelConfigResponse.from(
                modelConfigService.activeChatOrDefault(),
                modelConfigService.activeEmbeddingOrDefault()
        ));
    }

    /**
     * 更新 save 配置 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @PutMapping("/api/model-config")
    public ApiResponse<ModelConfigResponse> saveConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.save(request)));
    }

    /**
     * 测试 test 配置 是否可用。
     * <p>使用最小请求验证配置、网络和模型服务是否连通。</p>
     */
    @PostMapping("/api/model-config/test")
    public ApiResponse<ModelConfigTestResponse> testConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(modelConnectionTestService.test(modelConfigService.connectionTestConfig(request)));
    }

    /**
     * 拉取 fetch Models 数据。
     * <p>外部 HTTP 或模型提供商响应会在这里转换为本地 DTO。</p>
     */
    @PostMapping("/api/model-config/models")
    public ApiResponse<ModelOptionsResponse> fetchModels(@Valid @RequestBody ModelConfigRequest request) {
        // 调用外部模型服务接口，返回值需要在当前层转换为本地 DTO。
        return ApiResponse.ok(modelCatalogService.fetchModels(request));
    }

    /**
     * 解析并校验模型配置角色。
     * <p>只允许 {@code CHAT} 和 {@code EMBEDDING}，非法值会提前转为配置异常。</p>
     */
    private static ModelConfigRole parseRole(String role) {
        if (role == null || role.isBlank()) {
            throw new ModelConfigurationException("Invalid role: role is required. Must be CHAT or EMBEDDING.");
        }
        try {
            return ModelConfigRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ModelConfigurationException("Invalid role: " + role + ". Must be CHAT or EMBEDDING.");
        }
    }

}


