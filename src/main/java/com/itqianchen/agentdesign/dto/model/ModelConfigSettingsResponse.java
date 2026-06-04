package com.itqianchen.agentdesign.dto.model;

import java.util.List;

public record ModelConfigSettingsResponse(
        ActiveModelConfigsResponse active,
        String role,
        List<ModelConfigResponse> configs,
        ModelConfigResponse selectedConfig
) {
}
