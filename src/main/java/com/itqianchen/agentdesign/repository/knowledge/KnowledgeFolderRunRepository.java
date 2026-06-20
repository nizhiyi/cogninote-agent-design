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
     * 查询每个 scope 最近一次维护记录。
     *
     * @return 最近运行记录列表
     */
    public List<KnowledgeFolderRun> findLatestRunsByScope() {
        return mapper.findLatestRunsByScope();
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
