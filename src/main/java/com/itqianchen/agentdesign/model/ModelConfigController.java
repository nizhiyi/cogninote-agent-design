package com.itqianchen.agentdesign.model;

import com.itqianchen.agentdesign.chat.LlmGateway;
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
    public ModelConfigResponse getConfig() {
        return ModelConfigResponse.from(modelConfigService.activeOrDefault());
    }

    @PutMapping
    public ModelConfigResponse saveConfig(@Valid @RequestBody ModelConfigRequest request) {
        return ModelConfigResponse.from(modelConfigService.save(request));
    }

    @PostMapping("/test")
    public ModelConfigTestResponse testConfig(@Valid @RequestBody ModelConfigRequest request) {
        llmGateway.testConnection(modelConfigService.connectionTestConfig(request));
        return new ModelConfigTestResponse(true, "DashScope connection succeeded");
    }
}
