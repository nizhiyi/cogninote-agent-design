package com.itqianchen.agentdesign.domain.enums.knowledge;

/**
 * 知识库维护运行记录的作用范围。
 *
 * <p>枚举名会持久化到 knowledge_folder_runs.scope_type；ALL 的 scopeId 固定为空，目录范围必须带 scopeId。</p>
 */
public enum KnowledgeFolderRunScopeType {
    /** 全库维护操作。 */
    ALL,

    /** 单个知识库目录维护操作。 */
    KNOWLEDGE_FOLDER,

    /** 旧版本未归属文档的维护操作。 */
    UNASSIGNED
}
