package com.itqianchen.agentdesign.domain.dto.knowledge;

import java.util.List;

/**
 * 知识库维护队列快照。
 *
 * <p>该响应是前端维护任务 store 的恢复入口；SSE 丢事件或页面刷新后，前端应以这里的队列快照为准。</p>
 */
public record KnowledgeMaintenanceQueueResponse(
        List<KnowledgeFolderRunResponse> currentRuns,
        List<KnowledgeFolderRunResponse> queuedRuns,
        KnowledgeFolderRunResponse latestRun
) {
}
