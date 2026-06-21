package com.itqianchen.agentdesign.dto.knowledge;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量删除知识库维护记录请求。
 *
 * <p>删除只针对完成后的历史记录；排队和运行中的任务由维护队列接口管理。</p>
 */
public record KnowledgeFolderRunBatchDeleteRequest(
        @NotEmpty List<String> ids
) {
}
