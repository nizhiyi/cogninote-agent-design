package com.itqianchen.agentdesign.domain.exception.knowledge;

/**
 * 知识库维护任务操作异常。
 *
 * <p>用于表达用户可修正的队列操作错误，例如取消了非等待状态的任务。</p>
 */
public class KnowledgeMaintenanceException extends RuntimeException {

    public KnowledgeMaintenanceException(String message) {
        super(message);
    }
}
