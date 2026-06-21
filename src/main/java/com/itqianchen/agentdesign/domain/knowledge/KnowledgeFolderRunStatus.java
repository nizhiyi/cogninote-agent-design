package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库维护运行结果。
 *
 * <p>状态表示一次维护动作是否完成，不等同于目录健康状态；有失败项但主流程完成时应使用
 * COMPLETED_WITH_WARNINGS。</p>
 */
public enum KnowledgeFolderRunStatus {
    /** 操作正在执行，前端应禁用重复触发并展示运行中状态。 */
    RUNNING,

    /** 操作完成且没有报告失败项。 */
    COMPLETED,

    /** 操作完成，但存在解析失败或索引失败等需要用户关注的问题。 */
    COMPLETED_WITH_WARNINGS,

    /** 操作整体失败。 */
    FAILED
}
