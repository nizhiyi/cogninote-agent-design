package com.itqianchen.agentdesign.config;

import com.itqianchen.agentdesign.service.system.AppStorageInitializer;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SQLiteDataSourceConfig {

    private static final int SQLITE_POOL_SIZE = 1;

    private final AppStorageInitializer storageInitializer;

    public SQLiteDataSourceConfig(AppStorageInitializer storageInitializer) {
        this.storageInitializer = storageInitializer;
    }

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


