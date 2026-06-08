package com.itqianchen.agentdesign.service.system;

import com.itqianchen.agentdesign.dto.system.SystemStatusResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * System Status 服务 承载 系统状态 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class SystemStatusService {

    private static final String STATUS_UP = "UP";

    private final AppStorageInitializer storageInitializer;
    private final String version;
    private final boolean desktopMode;

    /**
     * 注入 SystemStatusService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SystemStatusService(
            AppStorageInitializer storageInitializer,
            @Value("${app.version:${project.version:0.0.1-SNAPSHOT}}") String version
    ) {
        this.storageInitializer = storageInitializer;
        this.version = version;
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
}
