package com.itqianchen.agentdesign.domain.storage;

import java.nio.file.Path;

public record AppStorage(
        Path baseDir,
        Path configDir,
        Path dataDir,
        Path databasePath,
        Path luceneIndexDir,
        Path logsDir
) {
}


