package com.itqianchen.agentdesign.domain.search;

/**
 * Embedding Unavailable 异常 表示 检索索引 的可识别异常。
 * <p>统一异常处理器会根据异常类型转换为稳定的 API 响应。</p>
 */
public class EmbeddingUnavailableException extends RuntimeException {

    /**
     * 注入 EmbeddingUnavailableException 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    /**
     * 注入 EmbeddingUnavailableException 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}


