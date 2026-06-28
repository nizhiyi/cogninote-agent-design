package com.itqianchen.agentdesign.domain.exception.websearch;

/**
 * 联网搜索配置不可用异常。
 *
 * <p>用户显式开启联网但全局设置缺失时抛出，调用方应把它作为可配置错误返回前端。</p>
 */
public class WebSearchConfigurationException extends RuntimeException {

    public WebSearchConfigurationException(String message) {
        super(message);
    }
}
