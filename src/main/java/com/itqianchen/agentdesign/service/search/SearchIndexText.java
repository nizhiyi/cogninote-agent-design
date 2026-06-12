package com.itqianchen.agentdesign.service.search;

/**
 * 写入 Lucene 的双通道文本。
 *
 * <p>proseText 保留自然语言检索权重，codeText 保留代码块内容，避免代码噪声稀释普通段落的 BM25 命中。</p>
 */
record SearchIndexText(
        String proseText,
        String codeText
) {
}
