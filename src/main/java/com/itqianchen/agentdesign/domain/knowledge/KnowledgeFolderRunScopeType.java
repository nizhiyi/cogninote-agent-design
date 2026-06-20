package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库维护运行记录的作用范围。
 */
public enum KnowledgeFolderRunScopeType {
    /** 全库维护操作。 */
    ALL,

    /** 单个知识库目录维护操作。 */
    KNOWLEDGE_FOLDER,

    /** 旧版本未归属文档的维护操作。 */
    UNASSIGNED
}
