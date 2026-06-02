package com.itqianchen.agentdesign.dto.model;

import java.util.List;

public record ModelOptionsResponse(
        List<ModelOptionResponse> models,
        long fetchedAt
) {
}
