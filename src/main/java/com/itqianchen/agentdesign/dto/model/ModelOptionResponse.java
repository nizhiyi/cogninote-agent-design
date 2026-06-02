package com.itqianchen.agentdesign.dto.model;

import com.itqianchen.agentdesign.domain.model.ModelCapability;

public record ModelOptionResponse(
        String id,
        String name,
        ModelCapability capability
) {
}
