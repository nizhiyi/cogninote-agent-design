package com.itqianchen.agentdesign.domain.exception.model;

/**
 * 模型配置不可用时抛出的业务异常。
 *
 * <p>该异常会被统一异常处理器转换为 400，前端据此保留设置页状态并提示用户修正配置。</p>
 */
public class ModelConfigurationException extends RuntimeException {

    /**
     * 使用可展示消息创建模型配置异常。
     *
     * @param message 模型配置失败原因
     */
    public ModelConfigurationException(String message) {
        super(message);
    }

    /**
     * 保留底层原因创建模型配置异常。
     *
     * @param message 模型配置失败原因
     * @param cause 底层 provider、URL 或客户端构造异常
     */
    public ModelConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}


