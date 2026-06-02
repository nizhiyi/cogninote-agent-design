package com.itqianchen.agentdesign.domain.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String baseDir, String databasePath) {
}


