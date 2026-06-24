package com.itqianchen.agentdesign.domain.properties.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Storage 配置属性 映射 系统状态 的 YAML 配置。
 * <p>通过类型化配置隔离环境变量、默认值和业务代码。</p>
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String baseDir, String databasePath) {
}


