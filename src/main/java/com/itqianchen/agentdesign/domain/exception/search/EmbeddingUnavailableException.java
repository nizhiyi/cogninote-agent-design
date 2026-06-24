package com.itqianchen.agentdesign.domain.exception.search;

/**
 * 向量能力不可用时抛出的检索异常。
 *
 * <p>该异常用于区分“用户未配置 embedding”与普通索引故障，前端可以给出配置引导。</p>
 */
public class EmbeddingUnavailableException extends RuntimeException {

    /**
     * 使用可展示消息创建向量不可用异常。
     *
     * @param message 向量能力不可用原因
     */
    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    /**
     * 保留底层原因创建向量不可用异常。
     *
     * @param message 向量能力不可用原因
     * @param cause 底层模型配置或调用异常
     */
    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}


