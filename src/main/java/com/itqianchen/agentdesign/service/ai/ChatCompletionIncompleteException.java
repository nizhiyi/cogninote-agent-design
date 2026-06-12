package com.itqianchen.agentdesign.service.ai;

/**
 * Chat Completion Incomplete 异常 表示 聊天会话 的可识别异常。
 * <p>统一异常处理器会根据异常类型转换为稳定的 API 响应。</p>
 */
public class ChatCompletionIncompleteException extends RuntimeException {

    /**
     * 创建模型回答不完整异常。
     *
     * @param message 面向前端提示的截断原因和处理建议
     */
    public ChatCompletionIncompleteException(String message) {
        super(message);
    }
}
