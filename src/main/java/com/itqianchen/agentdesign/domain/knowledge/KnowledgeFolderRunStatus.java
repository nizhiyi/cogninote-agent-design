package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库维护运行结果。
 */
public enum KnowledgeFolderRunStatus {
    /** 操作完成且没有报告失败项。 */
    COMPLETED,

    /** 操作完成，但存在解析失败或索引失败等需要用户关注的问题。 */
    COMPLETED_WITH_WARNINGS,

    /** 操作整体失败。 */
    FAILED
}
