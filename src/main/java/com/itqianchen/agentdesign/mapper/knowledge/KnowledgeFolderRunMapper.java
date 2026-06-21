package com.itqianchen.agentdesign.mapper.knowledge;

import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRun;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库维护运行记录的 MyBatis SQL 边界。
 */
public interface KnowledgeFolderRunMapper {

    /**
     * 插入维护运行记录。
     *
     * @param run 运行记录
     */
    void insertRun(KnowledgeFolderRun run);

    /**
     * 查询指定范围最近的维护运行记录。
     *
     * @param scopeType 范围类型；为空时不限制
     * @param scopeId 范围 ID；全库范围为空
     * @param limit 最大返回数量
     * @return 运行记录列表
     */
    List<KnowledgeFolderRun> findRuns(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId,
            @Param("limit") int limit
    );

    /**
     * 查询每个 scope 的最近一次运行记录。
     *
     * @return 每个 scope 的最近维护记录
     */
    List<KnowledgeFolderRun> findLatestRunsByScope();

    /**
     * 删除指定 scope 的维护运行记录。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全库范围为空
     * @return 删除的记录数量
     */
    int deleteByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") String scopeId
    );
}
