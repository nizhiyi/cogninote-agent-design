package com.itqianchen.agentdesign.mapper.model;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Model 配置 Mapper 声明 模型配置 相关的 MyBatis SQL 操作。
 * <p>方法签名需要和注解 SQL、数据库表结构保持一致。</p>
 */
public interface ModelConfigMapper {

    /**
     * 读取 find All 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ModelConfig> findAll(@Param("role") String role);

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ModelConfig> findById(@Param("id") String id);

    /**
     * 读取 find Active 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ModelConfig> findActive(@Param("role") String role);

    /**
     * 更新 save 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    void save(ModelConfig config);

    /**
     * 执行 模型配置 中的 deactivate Role 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void deactivateRole(@Param("role") String role, @Param("updatedAt") long updatedAt);

    /**
     * 执行 模型配置 中的 activate 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    void activate(@Param("id") String id, @Param("updatedAt") long updatedAt);

    /**
     * 删除 delete 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    void delete(@Param("id") String id);

    /**
     * 执行 模型配置 中的 count By Role 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    long countByRole(@Param("role") String role);

    /**
     * 读取 find Legacy Active 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    List<ModelConfig> findLegacyActive(@Param("id") String id);

    /**
     * 更新 save Active 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    void saveActive(ModelConfig config);
}
