package com.itqianchen.agentdesign.service.ai;

/**
 * Chat Completion Incomplete 异常 表示 聊天会话 的可识别异常。
 * <p>统一异常处理器会根据异常类型转换为稳定的 API 响应。</p>
 */
public class ChatCompletionIncompleteException extends RuntimeException {

    /**
     * 注入 ChatCompletionIncompleteException 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ChatCompletionIncompleteException(String message) {
        super(message);
    }
}
