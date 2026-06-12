package com.itqianchen.agentdesign.domain.search;

/**
 * Lucene 索引读写失败时抛出的技术异常。
 *
 * <p>统一异常处理器会记录堆栈并返回稳定错误码，调用方不应吞掉这类可重建但需要告警的问题。</p>
 */
public class SearchIndexException extends RuntimeException {

    /**
     * 保留底层原因创建索引异常。
     *
     * @param message 索引读写失败原因
     * @param cause 底层 Lucene 或文件系统异常
     */
    public SearchIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}


