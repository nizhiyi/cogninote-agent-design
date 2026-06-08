package com.itqianchen.agentdesign.service.search;

/**
 * Search Field Names 承担 检索索引 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
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
     * 注入 SearchFieldNames 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    private SearchFieldNames() {
    }
}


