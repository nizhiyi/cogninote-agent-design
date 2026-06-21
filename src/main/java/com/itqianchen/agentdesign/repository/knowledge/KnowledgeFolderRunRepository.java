package com.itqianchen.agentdesign.repository.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRun;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.mapper.knowledge.KnowledgeFolderRunMapper;
import java.util.List;
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
