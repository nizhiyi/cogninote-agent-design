package com.itqianchen.agentdesign.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String baseDir, String databasePath) {
}
