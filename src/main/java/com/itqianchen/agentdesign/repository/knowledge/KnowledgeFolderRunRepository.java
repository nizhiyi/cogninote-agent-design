package com.itqianchen.agentdesign.repository.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRun;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunOperation;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunStatus;
import com.itqianchen.agentdesign.mapper.knowledge.KnowledgeFolderRunMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 知识库维护运行记录仓储。
 */
@Repository
public class KnowledgeFolderRunRepository {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final KnowledgeFolderRunMapper mapper;

    /**
     * 注入维护运行记录 Mapper。
     *
     * @param mapper SQLite 运行记录访问接口
     */
    public KnowledgeFolderRunRepository(KnowledgeFolderRunMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存维护运行记录。
     *
     * @param run 运行记录
     */
    public void insert(KnowledgeFolderRun run) {
        mapper.insertRun(run);
    }

    public Optional<KnowledgeFolderRun> findById(String id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public Optional<KnowledgeFolderRun> findActiveByScopeAndOperation(
            KnowledgeFolderRunScopeType scopeType,
            String scopeId,
            KnowledgeFolderRunOperation operation
    ) {
        return Optional.ofNullable(mapper.findActiveByScopeAndOperation(scopeType.name(), scopeId, operation.name()));
    }

    public List<KnowledgeFolderRun> findActiveRuns() {
        return mapper.findActiveRuns();
    }

    public List<KnowledgeFolderRun> findQueuedRuns() {
        return mapper.findQueuedRuns();
    }

    public List<KnowledgeFolderRun> findQueueRuns() {
        return mapper.findQueueRuns();
    }

    /**
     * 查询指定范围最近的运行记录。
     *
     * @param scopeType 范围类型；为空时不限制
     * @param scopeId 范围 ID；全库范围为空
     * @param limit 最大返回数量
     * @return 运行记录列表
     */
    public List<KnowledgeFolderRun> findRuns(KnowledgeFolderRunScopeType scopeType, String scopeId, Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        return mapper.findRuns(scopeType == null ? null : scopeType.name(), scopeId, normalizedLimit);
    }

    /**
     * 分页查询维护运行记录。
     *
     * @param scopeType 范围类型；为空时不限制
     * @param scopeId 范围 ID；全库范围为空
     * @param page 页码，从 1 开始
     * @param pageSize 每页数量
     * @return 当前页运行记录
     */
    public List<KnowledgeFolderRun> findRunsPage(
            KnowledgeFolderRunScopeType scopeType,
            String scopeId,
            Integer page,
            Integer pageSize
    ) {
        int normalizedPageSize = normalizePageSize(pageSize);
        int offset = normalizeOffset(page, normalizedPageSize);
        return mapper.findRunsPage(
                scopeType == null ? null : scopeType.name(),
                scopeId,
                normalizedPageSize,
                offset
        );
    }

    /**
     * 统计维护运行记录数量。
     *
     * @param scopeType 范围类型；为空时不限制
     * @param scopeId 范围 ID；全库范围为空
     * @return 记录数量
     */
    public long countRuns(KnowledgeFolderRunScopeType scopeType, String scopeId) {
        return mapper.countRuns(scopeType == null ? null : scopeType.name(), scopeId);
    }

    /**
     * 查询每个 scope 最近一次维护记录。
     *
     * @return 最近运行记录列表
     */
    public List<KnowledgeFolderRun> findLatestRunsByScope() {
        return mapper.findLatestRunsByScope();
    }

    public Optional<KnowledgeFolderRun> findLatestRun() {
        return Optional.ofNullable(mapper.findLatestRun());
    }

    public int markStarted(String id, String phase, long progressTotal, String currentItem, long startedAt) {
        return mapper.markStarted(id, phase, progressTotal, currentItem, startedAt);
    }

    public int updateProgress(String id, String phase, long progressCurrent, long progressTotal, String currentItem) {
        return mapper.updateProgress(id, phase, progressCurrent, progressTotal, currentItem, System.currentTimeMillis());
    }

    public int markCancelling(String id) {
        return mapper.markCancelling(id, System.currentTimeMillis());
    }

    public int markCancelled(String id, String message) {
        return mapper.markCancelled(id, message, System.currentTimeMillis());
    }

    public int markCompleted(
            String id,
            KnowledgeFolderRunStatus status,
            int scannedCount,
            int parsedCount,
            int skippedCount,
            int failedCount,
            long indexedDocumentCount,
            long indexedChunkCount,
            long failedDocumentCount,
            String failuresJson,
            long progressCurrent,
            long progressTotal
    ) {
        return mapper.markCompleted(
                id,
                status.name(),
                scannedCount,
                parsedCount,
                skippedCount,
                failedCount,
                indexedDocumentCount,
                indexedChunkCount,
                failedDocumentCount,
                failuresJson,
                progressCurrent,
                progressTotal,
                System.currentTimeMillis()
        );
    }

    public int markFailed(String id, String message) {
        return mapper.markFailed(id, message, System.currentTimeMillis());
    }

    public int cleanupInterruptedRuns() {
        return mapper.cleanupInterruptedRuns(
                System.currentTimeMillis(),
                "应用重启，排队中的维护任务已取消。",
                "应用重启，运行中的维护任务已中断。"
        );
    }

    /**
     * 删除指定范围的运行记录。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全库范围为空
     * @return 删除的记录数量
     */
    public int deleteByScope(KnowledgeFolderRunScopeType scopeType, String scopeId) {
        return mapper.deleteByScope(scopeType.name(), scopeId);
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_LIMIT);
    }

    private static int normalizeOffset(Integer page, int pageSize) {
        int normalizedPage = page == null || page <= 0 ? 1 : page;
        return (normalizedPage - 1) * pageSize;
    }
}
