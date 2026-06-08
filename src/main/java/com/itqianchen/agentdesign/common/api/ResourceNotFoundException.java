package com.itqianchen.agentdesign.common.api;

/**
 * Resource Not Found 异常 表示 通用 API 的可识别异常。
 * <p>统一异常处理器会根据异常类型转换为稳定的 API 响应。</p>
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 注入 ResourceNotFoundException 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}


