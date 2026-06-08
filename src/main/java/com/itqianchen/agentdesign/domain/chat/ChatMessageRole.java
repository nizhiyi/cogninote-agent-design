package com.itqianchen.agentdesign.domain.chat;

/**
 * Chat Message Role 枚举 聊天会话 的稳定取值。
 * <p>枚举值可能进入数据库或 API 响应，修改时需要考虑兼容性。</p>
 */
public enum ChatMessageRole {
    USER,
    ASSISTANT
}
