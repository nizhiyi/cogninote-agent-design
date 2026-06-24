package com.itqianchen.agentdesign.domain.enums.document;

/**
 * Document Status 枚举 文档管理 的稳定取值。
 * <p>枚举值可能进入数据库或 API 响应，修改时需要考虑兼容性。</p>
 */
public enum DocumentStatus {
    PARSED,
    SKIPPED,
    FAILED
}


