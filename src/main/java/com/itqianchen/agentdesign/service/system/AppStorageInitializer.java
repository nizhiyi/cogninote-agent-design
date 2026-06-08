package com.itqianchen.agentdesign.service.system;

import com.itqianchen.agentdesign.domain.storage.AppStorage;
import com.itqianchen.agentdesign.domain.storage.StorageInitializationException;
import com.itqianchen.agentdesign.domain.storage.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * App Storage 初始化器 在应用启动时准备 系统状态 资源。
 * <p>启动阶段副作用需要保持幂等，避免重复运行破坏已有数据。</p>
 */
@Component
public class AppStorageInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final String APP_DATA_ENV = "APPDATA";
    private static final String DEFAULT_APP_DIR = "CogniNote";

    private final StorageProperties storageProperties;
    private final AppStorage appStorage;

    /**
     * 注入 AppStorageInitializer 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public AppStorageInitializer(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        Path baseDir = resolveBaseDir(storageProperties.baseDir());
        Path dataDir = baseDir.resolve("data");
        this.appStorage = new AppStorage(
                baseDir,
                baseDir.resolve("config"),
                dataDir,
                resolveDatabasePath(storageProperties.databasePath(), dataDir.resolve("cogninote.db")),
                baseDir.resolve("index").resolve("lucene"),
                baseDir.resolve("logs")
        );
    }

    /**
     * 执行 系统状态 中的 app Storage 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public AppStorage appStorage() {
        return appStorage;
    }

    /**
     * 响应 on Application 事件 生命周期事件。
     * <p>常用于应用启动、框架回调或资源初始化场景。</p>
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        /**
         * 确保 ensure Initialized 所需前置条件存在。
         * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
         */
        ensureInitialized();
    }

    /**
     * 确保 ensure Initialized 所需前置条件存在。
     * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
     */
    public void ensureInitialized() {
        /**
         * 创建 create Directory 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        createDirectory(appStorage.baseDir());
        /**
         * 创建 create Directory 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        createDirectory(appStorage.configDir());
        /**
         * 创建 create Directory 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        createDirectory(appStorage.dataDir());
        /**
         * 创建 create Directory 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        createDirectory(appStorage.databasePath().getParent());
        /**
         * 创建 create Directory 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        createDirectory(appStorage.luceneIndexDir());
        /**
         * 创建 create Directory 对应的数据。
         * <p>创建流程集中处理默认值、校验和持久化边界。</p>
         */
        createDirectory(appStorage.logsDir());
    }

    /**
     * 解析 resolve Base Dir 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    private Path resolveBaseDir(String configuredBaseDir) {
        if (StringUtils.hasText(configuredBaseDir)) {
            return Path.of(configuredBaseDir).toAbsolutePath().normalize();
        }

        String appData = System.getenv(APP_DATA_ENV);
        if (StringUtils.hasText(appData)) {
            return Path.of(appData, DEFAULT_APP_DIR).toAbsolutePath().normalize();
        }

        // 非 Windows 开发环境不一定有 APPDATA，保留 home 目录兜底方便测试和跨平台开发。
        return Path.of(System.getProperty("user.home"), "." + DEFAULT_APP_DIR.toLowerCase()).toAbsolutePath().normalize();
    }

    /**
     * 解析 resolve Database Path 的最终取值。
     * <p>默认值、兼容规则和异常输入兜底集中在这里。</p>
     */
    private Path resolveDatabasePath(String configuredDatabasePath, Path defaultDatabasePath) {
        if (StringUtils.hasText(configuredDatabasePath)) {
            return Path.of(configuredDatabasePath).toAbsolutePath().normalize();
        }

        return defaultDatabasePath.toAbsolutePath().normalize();
    }

    /**
     * 创建 create Directory 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    private void createDirectory(Path directory) {
        try {
            // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new StorageInitializationException("Failed to create application directory: " + directory, ex);
        }
    }
}


