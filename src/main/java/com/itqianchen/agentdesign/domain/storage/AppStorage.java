package com.itqianchen.agentdesign.domain.storage;

import java.nio.file.Path;

/**
 * App Storage 是 系统状态 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record AppStorage(
        Path baseDir,
        Path configDir,
        Path dataDir,
        Path databasePath,
        Path luceneIndexDir,
        Path logsDir
) {
}


