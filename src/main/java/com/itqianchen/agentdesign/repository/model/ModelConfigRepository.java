package com.itqianchen.agentdesign.repository.model;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.mapper.model.ModelConfigMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 模型配置仓储。
 *
 * <p>负责维护每个 role 只有一个 active 配置的应用层约束，并保留旧版 active_model_config
 * 接口的迁移兼容入口。</p>
 */
@Repository
public class ModelConfigRepository {

    private final ModelConfigMapper modelConfigMapper;

    /**
     * 注入模型配置 Mapper。
     *
     * @param modelConfigMapper SQLite 模型配置访问接口
     */
    public ModelConfigRepository(ModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    /**
     * 查询指定角色的全部模型配置。
     *
     * @param role 模型角色
     * @return 配置列表
     */
    public List<ModelConfig> findAll(ModelConfigRole role) {
        return modelConfigMapper.findAll(role.name());
    }

    /**
     * 按 ID 查询模型配置。
     *
     * @param id 配置 ID
     * @return 配置；不存在时为空
     */
    public Optional<ModelConfig> findById(String id) {
        return modelConfigMapper.findById(id).stream().findFirst();
    }

    /**
     * 查询指定角色当前激活配置。
     *
     * @param role 模型角色
     * @return 激活配置；未配置时为空
     */
    public Optional<ModelConfig> findActive(ModelConfigRole role) {
        return modelConfigMapper.findActive(role.name()).stream().findFirst();
    }

    /**
     * 查询当前激活的 Chat 配置。
     *
     * @return 激活 Chat 配置
     */
    public Optional<ModelConfig> findActiveChat() {
        return findActive(ModelConfigRole.CHAT);
    }

    /**
     * 查询当前激活的 Embedding 配置。
     *
     * @return 激活 Embedding 配置
     */
    public Optional<ModelConfig> findActiveEmbedding() {
        return findActive(ModelConfigRole.EMBEDDING);
    }

    /**
     * 旧版入口，固定返回 Chat 角色激活配置。
     *
     * @return 激活 Chat 配置
     */
    @Deprecated
    public Optional<ModelConfig> findActive() {
        return findActiveChat();
    }

    /**
     * 保存模型配置并重新读取数据库结果。
     *
     * <p>重新读取用于拿到 Mapper 层默认值和 SQLite 实际落库状态。</p>
     *
     * @param config 待保存配置
     * @return 保存后的配置
     */
    public ModelConfig save(ModelConfig config) {
        modelConfigMapper.save(config);
        return findById(config.id()).orElseThrow(() ->
                new IllegalStateException("Model config was not found after save"));
    }

    /**
     * 激活指定模型配置。
     *
     * <p>每个角色只能有一个 active 配置，先清同角色旧 active 再设置目标配置。</p>
     *
     * @param id 配置 ID
     * @param updatedAt 更新时间戳
     * @return 激活后的配置
     */
    public ModelConfig activate(String id, long updatedAt) {
        ModelConfig target = findById(id).orElseThrow(() ->
                new ModelConfigurationException("Model config not found: " + id));
        // SQLite 没有 partial unique index 的跨版本保证时，应用层事务先清后设。
        // 这个约束是模型选择的核心：每个角色只能有一个 active 配置。
        modelConfigMapper.deactivateRole(target.role().name(), updatedAt);
        modelConfigMapper.activate(id, updatedAt);
        return findById(id).orElseThrow(() ->
                new IllegalStateException("Model config was not found after activate"));
    }

    /**
     * 删除模型配置。
     *
     * @param id 配置 ID
     */
    public void delete(String id) {
        modelConfigMapper.delete(id);
    }

    /**
     * 统计指定角色配置数量。
     *
     * @param role 模型角色
     * @return 配置数量
     */
    public long countByRole(ModelConfigRole role) {
        return modelConfigMapper.countByRole(role.name());
    }

    /**
     * 查询旧版单行激活配置。
     *
     * <p>该入口只服务旧版 active_model_config 迁移；新代码必须按 Chat/Embedding 角色分别读取激活配置。</p>
     *
     * @return 旧版配置；不存在时为空
     */
    @Deprecated
    public Optional<ModelConfig> findLegacyActive() {
        return modelConfigMapper.findLegacyActive(ModelConfigDefaults.ACTIVE_CONFIG_ID).stream().findFirst();
    }

    /**
     * 保存旧版单行激活配置。
     *
     * <p>用于兼容旧设置接口，迁移后的多角色配置不应通过该方法写入。</p>
     *
     * @param config 旧版配置
     * @return 保存后的旧版配置
     */
    @Deprecated
    public ModelConfig saveActive(ModelConfig config) {
        modelConfigMapper.saveActive(new ModelConfig(
                config.id(),
                config.role(),
                config.provider(),
                config.displayName(),
                config.baseUrl(),
                config.apiKey(),
                config.modelName(),
                config.resolvedEmbeddingDimensions(),
                config.resolvedTemperature(),
                config.resolvedDefaultTopK(),
                config.contextWindowTokens(),
                config.active(),
                config.createdAt(),
                config.updatedAt()
        ));
        return findLegacyActive().orElseThrow(() ->
                new IllegalStateException("Model config was not found after save"));
    }
}
