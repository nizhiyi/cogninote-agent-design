package com.itqianchen.agentdesign.domain.search;

/**
 * Search Mode 枚举 检索索引 的稳定取值。
 * <p>枚举值可能进入数据库或 API 响应，修改时需要考虑兼容性。</p>
 */
public enum SearchMode {
    KEYWORD,
    VECTOR,
    HYBRID
}


