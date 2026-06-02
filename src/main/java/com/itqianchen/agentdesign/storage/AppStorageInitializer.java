package com.itqianchen.agentdesign.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AppStorageInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final String APP_DATA_ENV = "APPDATA";
    private static final String DEFAULT_APP_DIR = "CogniNote";

    private final StorageProperties storageProperties;
    private final AppStorage appStorage;

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

    public AppStorage appStorage() {
        return appStorage;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ensureInitialized();
    }

    public void ensureInitialized() {
        createDirectory(appStorage.baseDir());
        createDirectory(appStorage.configDir());
        createDirectory(appStorage.dataDir());
        createDirectory(appStorage.databasePath().getParent());
        createDirectory(appStorage.luceneIndexDir());
        createDirectory(appStorage.logsDir());
    }

    private Path resolveBaseDir(String configuredBaseDir) {
        if (StringUtils.hasText(configuredBaseDir)) {
            return Path.of(configuredBaseDir).toAbsolutePath().normalize();
        }

        String appData = System.getenv(APP_DATA_ENV);
        if (StringUtils.hasText(appData)) {
            return Path.of(appData, DEFAULT_APP_DIR).toAbsolutePath().normalize();
        }

        // Non-Windows development environments do not always expose APPDATA.
        return Path.of(System.getProperty("user.home"), "." + DEFAULT_APP_DIR.toLowerCase()).toAbsolutePath().normalize();
    }

    private Path resolveDatabasePath(String configuredDatabasePath, Path defaultDatabasePath) {
        if (StringUtils.hasText(configuredDatabasePath)) {
            return Path.of(configuredDatabasePath).toAbsolutePath().normalize();
        }

        return defaultDatabasePath.toAbsolutePath().normalize();
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw new StorageInitializationException("Failed to create application directory: " + directory, ex);
        }
    }
}
