package com.itqianchen.agentdesign.service.search;

/**
 * Search Index Text 是 检索索引 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
record SearchIndexText(
        String proseText,
        String codeText
) {
}
