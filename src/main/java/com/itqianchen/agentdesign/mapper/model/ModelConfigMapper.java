package com.itqianchen.agentdesign.mapper.model;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 模型配置表的 MyBatis SQL 边界。
 *
 * <p>CHAT 和 EMBEDDING 共用一张表，每个 role 只能有一个 active 配置；唯一性由
 * ModelConfigRepository 的事务顺序维护，避免不同 SQLite 版本对 partial index 的支持差异。</p>
 */
public interface ModelConfigMapper {

    /**
     * 查询指定角色的所有配置。
     *
     * @param role 模型角色名称
     * @return 配置列表
     */
    List<ModelConfig> findAll(@Param("role") String role);

    /**
     * 按 ID 查询配置。
     *
     * @param id 配置 ID
     * @return 配置记录
     */
    List<ModelConfig> findById(@Param("id") String id);

    /**
     * 查询指定角色的 active 配置。
     *
     * @param role 模型角色名称
     * @return active 配置记录
     */
    List<ModelConfig> findActive(@Param("role") String role);

    /**
     * 新增或更新模型配置。
     *
     * @param config 模型配置
     */
    void save(ModelConfig config);

    /**
     * 清除指定角色的 active 标记。
     *
     * <p>激活新配置前必须先执行该操作，保证运行时不会读到多个 active 模型。</p>
     *
     * @param role 模型角色名称
     * @param updatedAt 更新时间戳
     */
    void deactivateRole(@Param("role") String role, @Param("updatedAt") long updatedAt);

    /**
     * 将指定配置标记为 active。
     *
     * @param id 配置 ID
     * @param updatedAt 更新时间戳
     */
    void activate(@Param("id") String id, @Param("updatedAt") long updatedAt);

    /**
     * 删除模型配置。
     *
     * @param id 配置 ID
     */
    void delete(@Param("id") String id);

    /**
     * 统计指定角色配置数量。
     *
     * @param role 模型角色名称
     * @return 配置数量
     */
    long countByRole(@Param("role") String role);
}
