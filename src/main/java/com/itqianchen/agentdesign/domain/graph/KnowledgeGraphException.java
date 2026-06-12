package com.itqianchen.agentdesign.domain.graph;

/**
 * 知识图谱模块的可预期业务异常。
 */
public class KnowledgeGraphException extends RuntimeException {

    /**
     * 使用可展示消息创建图谱业务异常。
     *
     * @param message 图谱生成、抽取或合并失败原因
     */
    public KnowledgeGraphException(String message) {
        super(message);
    }

    /**
     * 保留底层原因创建图谱业务异常。
     *
     * @param message 图谱生成、抽取或合并失败原因
     * @param cause 底层模型调用、持久化或解析异常
     */
    public KnowledgeGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
