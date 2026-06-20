package com.itqianchen.agentdesign.domain.knowledge;

/**
 * 知识库维护动作类型。
 *
 * <p>枚举名会持久化到 knowledge_folder_runs.operation，并直接返回给前端展示；改名需要兼容旧库数据。</p>
 */
public enum KnowledgeFolderRunOperation {
    /** 导入本地目录。 */
    IMPORT,

    /** 同步本地目录差异。 */
    SYNC,

    /** 重建目录或全库检索索引。 */
    REBUILD_INDEX,

    /** 启用知识库目录。 */
    ENABLE,

    /** 停用知识库目录。 */
    DISABLE,

    /** 删除应用内目录记录。 */
    DELETE
}
