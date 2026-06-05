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

@RestController
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final ModelCatalogService modelCatalogService;
    private final ModelConnectionTestService modelConnectionTestService;

    public ModelConfigController(
            ModelConfigService modelConfigService,
            ModelCatalogService modelCatalogService,
            ModelConnectionTestService modelConnectionTestService
    ) {
        this.modelConfigService = modelConfigService;
        this.modelCatalogService = modelCatalogService;
        this.modelConnectionTestService = modelConnectionTestService;
    }

    @GetMapping("/api/model-configs")
    public ApiResponse<List<ModelConfigResponse>> listConfigs(@RequestParam String role) {
        return ApiResponse.ok(modelConfigService.list(parseRole(role)).stream()
                .map(ModelConfigResponse::from)
                .toList());
    }

    @GetMapping("/api/model-configs/active")
    public ApiResponse<ActiveModelConfigsResponse> activeConfigs() {
        return ApiResponse.ok(new ActiveModelConfigsResponse(
                ModelConfigResponse.from(modelConfigService.activeChatOrDefault()),
                ModelConfigResponse.from(modelConfigService.activeEmbeddingOrDefault())
        ));
    }

    @GetMapping("/api/model-configs/settings")
    public ApiResponse<ModelConfigSettingsResponse> settingsSnapshot(@RequestParam String role) {
        return ApiResponse.ok(modelConfigService.settingsSnapshot(parseRole(role)));
    }

    @PostMapping("/api/model-configs/settings/configs")
    public ApiResponse<ModelConfigSettingsResponse> createSettingsConfig(
            @Valid @RequestBody ModelConfigUpsertRequest request
    ) {
        return ApiResponse.ok(modelConfigService.createSettings(request));
    }

    @PutMapping("/api/model-configs/settings/configs/{id}")
    public ApiResponse<ModelConfigSettingsResponse> updateSettingsConfig(
            @PathVariable String id,
            @Valid @RequestBody ModelConfigUpsertRequest request
    ) {
        return ApiResponse.ok(modelConfigService.updateSettings(id, request));
    }

    @DeleteMapping("/api/model-configs/settings/configs/{id}")
    public ApiResponse<ModelConfigSettingsResponse> deleteSettingsConfig(@PathVariable String id) {
        return ApiResponse.ok(modelConfigService.deleteSettings(id));
    }

    @PostMapping("/api/model-configs/settings/configs/{id}/activate")
    public ApiResponse<ModelConfigSettingsResponse> activateSettingsConfig(@PathVariable String id) {
        return ApiResponse.ok(modelConfigService.activateSettings(id));
    }

    @PostMapping("/api/model-configs")
    public ApiResponse<ModelConfigResponse> createConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.create(request)));
    }

    @PutMapping("/api/model-configs/{id}")
    public ApiResponse<ModelConfigResponse> updateConfig(
            @PathVariable String id,
            @Valid @RequestBody ModelConfigRequest request
    ) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.update(id, request)));
    }

    @DeleteMapping("/api/model-configs/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable String id) {
        modelConfigService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/model-configs/{id}/activate")
    public ApiResponse<ModelConfigResponse> activateConfig(@PathVariable String id) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.activate(id)));
    }

    @PostMapping("/api/model-configs/test")
    public ApiResponse<ModelConfigTestResponse> testRoleConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(modelConnectionTestService.test(modelConfigService.connectionTestConfig(request)));
    }

    @PostMapping("/api/model-configs/models")
    public ApiResponse<ModelOptionsResponse> fetchRoleModels(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(modelCatalogService.fetchModels(request));
    }

    @GetMapping("/api/model-config")
    public ApiResponse<LegacyModelConfigResponse> getConfig() {
        return ApiResponse.ok(LegacyModelConfigResponse.from(
                modelConfigService.activeChatOrDefault(),
                modelConfigService.activeEmbeddingOrDefault()
        ));
    }

    @PutMapping("/api/model-config")
    public ApiResponse<ModelConfigResponse> saveConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.save(request)));
    }

    @PostMapping("/api/model-config/test")
    public ApiResponse<ModelConfigTestResponse> testConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(modelConnectionTestService.test(modelConfigService.connectionTestConfig(request)));
    }

    @PostMapping("/api/model-config/models")
    public ApiResponse<ModelOptionsResponse> fetchModels(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(modelCatalogService.fetchModels(request));
    }

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


