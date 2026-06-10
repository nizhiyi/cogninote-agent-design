package com.itqianchen.agentdesign.service.system;

import com.itqianchen.agentdesign.dto.system.SystemStatusResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * System Status 服务 承载 系统状态 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class SystemStatusService {

    private static final String STATUS_UP = "UP";
    private static final String FALLBACK_VERSION = "dev";

    private final AppStorageInitializer storageInitializer;
    private final String version;
    private final boolean desktopMode;

    /**
     * 注入 SystemStatusService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SystemStatusService(
            AppStorageInitializer storageInitializer,
            @Value("${app.version:}") String configuredVersion,
            ObjectProvider<BuildProperties> buildPropertiesProvider
    ) {
        this.storageInitializer = storageInitializer;
        this.version = resolveVersion(configuredVersion, buildPropertiesProvider.getIfAvailable());
        this.desktopMode = Boolean.parseBoolean(System.getenv().getOrDefault("COGNINOTE_DESKTOP", "false"));
    }

    /**
     * 执行 系统状态 中的 status 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public SystemStatusResponse status() {
        return new SystemStatusResponse(
                "CogniNote Agent",
                version,
                STATUS_UP,
                storageInitializer.appStorage().baseDir().toString(),
                desktopMode
        );
    }

    /**
     * 解析对外展示的后端版本。
     * <p>优先允许部署环境用 app.version 显式覆盖；默认读取 Maven 打包生成的 build-info，避免运行时读取不到
     * project.version 时回落到误导性的 SNAPSHOT 兜底值。</p>
     */
    static String resolveVersion(String configuredVersion, BuildProperties buildProperties) {
        if (StringUtils.hasText(configuredVersion)) {
            return configuredVersion.trim();
        }
        if (buildProperties != null && StringUtils.hasText(buildProperties.getVersion())) {
            return buildProperties.getVersion();
        }
        return FALLBACK_VERSION;
    }
}
