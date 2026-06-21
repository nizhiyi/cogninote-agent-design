package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库健康诊断问题类型。
 *
 * <p>枚举名是前后端识别问题类型的稳定协议，新增类型时需要同步前端展示和推荐动作映射。</p>
 */
public enum KnowledgeHealthIssueCode {
    /** 知识库目录路径当前不可访问。 */
    FOLDER_NOT_FOUND,

    /** 启用目录中没有文档记录。 */
    NO_DOCUMENTS,

    /** 存在解析失败的文档。 */
    PARSE_FAILED,

    /** 已解析文档尚未写入检索索引。 */
    UNINDEXED_DOCUMENTS,

    /** 本地文件元数据与上次解析记录不一致。 */
    STALE_LOCAL_FILES,

    /** 本地目录中存在尚未同步到应用的支持文件。 */
    NEW_LOCAL_FILES,

    /** 应用记录中的本地文件已经不存在。 */
    MISSING_LOCAL_FILES,

    /** 目录已停用。 */
    DISABLED_FOLDER,

    /** SQLite 事实与 Lucene 索引统计不一致。 */
    INDEX_INCONSISTENT,

    /** 当前未配置可用 Embedding，向量或混合检索会降级。 */
    EMBEDDING_UNCONFIGURED
}
