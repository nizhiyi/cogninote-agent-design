package com.itqianchen.agentdesign.dto.knowledge;

import java.util.List;

/**
 * 知识库维护记录删除响应。
 *
 * <p>批量删除会跳过排队和运行中的任务，调用方可用 skippedIds 向用户解释未删除的记录。</p>
 */
public record KnowledgeFolderRunDeleteResponse(
        int deletedCount,
        int skippedCount,
        List<String> skippedIds
) {
}
