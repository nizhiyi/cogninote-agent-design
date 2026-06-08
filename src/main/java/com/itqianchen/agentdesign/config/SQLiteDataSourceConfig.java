package com.itqianchen.agentdesign.config;

import com.itqianchen.agentdesign.service.system.AppStorageInitializer;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SQLite Data Source 配置 集中维护 业务 相关的 Spring 配置。
 * <p>这里的 Bean 或扫描配置会影响应用启动阶段的基础设施装配。</p>
 */
@Configuration
public class SQLiteDataSourceConfig {

    private static final int SQLITE_POOL_SIZE = 1;

    private final AppStorageInitializer storageInitializer;

    /**
     * 注入 SQLiteDataSourceConfig 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SQLiteDataSourceConfig(AppStorageInitializer storageInitializer) {
        this.storageInitializer = storageInitializer;
    }

    /**
     * 执行 业务 中的 data Source 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Bean
    public DataSource dataSource() {
        // Hikari 创建 DataSource 时可能立刻打开 SQLite 文件，早于 ApplicationReadyEvent。
        // 因此这里必须先确保 data 目录存在，避免首次启动因为数据库路径不存在失败。
        storageInitializer.ensureInitialized();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:" + storageInitializer.appStorage().databasePath());
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setMaximumPoolSize(SQLITE_POOL_SIZE);
        dataSource.setMinimumIdle(SQLITE_POOL_SIZE);
        dataSource.setPoolName("CogniNoteSQLitePool");
        return dataSource;
    }
}


