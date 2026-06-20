package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库当前可信状态。
 *
 * <p>状态由健康诊断即时派生，不持久化；排序和样式由前端按这些枚举名映射。</p>
 */
public enum KnowledgeHealthStatus {
    /** 当前没有发现影响搜索或 RAG 完整性的已知问题。 */
    HEALTHY,

    /** 存在部分失败或疑似变化，知识库仍可使用但结果可能不完整。 */
    WARNING,

    /** 存在目录不可访问或大面积未索引等会明显影响可信度的问题。 */
    ERROR,

    /** 目录已停用，不参与搜索和 RAG。 */
    DISABLED,

    /** 没有可用文档。 */
    EMPTY
}
