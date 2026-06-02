package com.itqianchen.agentdesign.controller.model;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.chat.LlmGateway;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.dto.model.ModelConfigResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigTestResponse;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final LlmGateway llmGateway;

    public ModelConfigController(ModelConfigService modelConfigService, LlmGateway llmGateway) {
        this.modelConfigService = modelConfigService;
        this.llmGateway = llmGateway;
    }

    @GetMapping
    public ApiResponse<ModelConfigResponse> getConfig() {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.activeOrDefault()));
    }

    @PutMapping
    public ApiResponse<ModelConfigResponse> saveConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ApiResponse.ok(ModelConfigResponse.from(modelConfigService.save(request)));
    }

    @PostMapping("/test")
    public ApiResponse<ModelConfigTestResponse> testConfig(@Valid @RequestBody ModelConfigRequest request) {
        llmGateway.testConnection(modelConfigService.connectionTestConfig(request));
        return ApiResponse.ok(new ModelConfigTestResponse(true, "DashScope connection succeeded"));
    }
}


