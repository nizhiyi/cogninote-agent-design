package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库健康诊断问题类型。
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

    /** 应用记录中的本地文件已经不存在。 */
    MISSING_LOCAL_FILES,

    /** 目录已停用。 */
    DISABLED_FOLDER
}
