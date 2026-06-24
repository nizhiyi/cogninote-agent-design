package com.itqianchen.agentdesign.domain.exception.ingestion;

/**
 * 文档解析阶段的可恢复异常。
 *
 * <p>该异常会被统一异常处理器转换为 400，消息需要能帮助用户修正文件类型、路径或文件内容。</p>
 */
public class DocumentParseException extends RuntimeException {

    /**
     * 使用可展示消息创建解析异常。
     *
     * @param message 文档解析失败原因
     */
    public DocumentParseException(String message) {
        super(message);
    }

    /**
     * 保留底层原因创建解析异常。
     *
     * @param message 文档解析失败原因
     * @param cause 底层 I/O 或解析库异常
     */
    public DocumentParseException(String message, Throwable cause) {
        super(message, cause);
    }
}


