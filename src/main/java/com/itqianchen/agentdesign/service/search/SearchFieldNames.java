package com.itqianchen.agentdesign.service.search;

/**
 * Lucene 索引字段名集合。
 *
 * <p>字段名同时被写入和查询逻辑使用，修改时必须同步迁移现有索引或触发全量重建。</p>
 */
final class SearchFieldNames {

    static final String CHUNK_ID = "chunk_id";
    static final String DOCUMENT_ID = "document_id";
    static final String FILE_NAME = "file_name";
    static final String SOURCE_PATH = "source_path";
    static final String CHUNK_INDEX = "chunk_index";
    static final String HEADING = "heading";
    static final String PAGE_NUMBER = "page_number";
    static final String CONTENT_HASH = "content_hash";
    static final String CONTENT = "content_for_bm25";
    static final String CODE_CONTENT = "content_for_code";
    static final String PREVIEW = "preview_text";
    static final String EMBEDDING = "embedding_vector";

    /**
     * 常量集合不允许实例化。
     */
    private SearchFieldNames() {
    }
}


