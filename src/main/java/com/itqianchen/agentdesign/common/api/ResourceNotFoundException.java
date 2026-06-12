package com.itqianchen.agentdesign.common.api;

/**
 * 通用资源不存在异常。
 *
 * <p>抛出该异常的服务方法会被统一异常处理器转换为 404 和稳定错误码，调用方不需要自行包装响应。</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 使用可展示消息创建资源不存在异常。
     *
     * @param message 面向前端或日志的资源不存在原因
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}


