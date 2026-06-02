package com.itqianchen.agentdesign.system;

public record SystemStatusResponse(
        String appName,
        String version,
        String status,
        String dataDir
) {
}
