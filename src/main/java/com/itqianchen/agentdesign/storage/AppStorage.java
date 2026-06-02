package com.itqianchen.agentdesign.storage;

import java.nio.file.Path;

public record AppStorage(
        Path baseDir,
        Path configDir,
        Path dataDir,
        Path luceneIndexDir,
        Path logsDir
) {
}
