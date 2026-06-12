package com.itqianchen.agentdesign.domain.storage;

/**
 * 应用存储目录初始化失败时抛出的启动异常。
 *
 * <p>这类失败通常表示本地目录权限、磁盘或路径不可用，服务不应带着不完整存储继续运行。</p>
 */
public class StorageInitializationException extends RuntimeException {

    /**
     * 保留底层原因创建存储初始化异常。
     *
     * @param message 存储初始化失败原因
     * @param cause 底层文件系统异常
     */
    public StorageInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}


