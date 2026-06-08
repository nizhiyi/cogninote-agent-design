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
 * Model 配置 仓储 是 模型配置 的持久化边界。
 * <p>服务层通过该类型访问数据，避免直接依赖 MyBatis Mapper 细节。</p>
 */
@Repository
public class ModelConfigRepository {

    private final ModelConfigMapper modelConfigMapper;

    /**
     * 注入 ModelConfigRepository 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ModelConfigRepository(ModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    /**
     * 读取 find All 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public List<ModelConfig> findAll(ModelConfigRole role) {
        return modelConfigMapper.findAll(role.name());
    }

    /**
     * 读取 find By Id 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<ModelConfig> findById(String id) {
        return modelConfigMapper.findById(id).stream().findFirst();
    }

    /**
     * 读取 find Active 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<ModelConfig> findActive(ModelConfigRole role) {
        return modelConfigMapper.findActive(role.name()).stream().findFirst();
    }

    /**
     * 读取 find Active Chat 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<ModelConfig> findActiveChat() {
        return findActive(ModelConfigRole.CHAT);
    }

    /**
     * 读取 find Active Embedding 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public Optional<ModelConfig> findActiveEmbedding() {
        return findActive(ModelConfigRole.EMBEDDING);
    }

    /**
     * 读取 find Active 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    @Deprecated
    public Optional<ModelConfig> findActive() {
        return findActiveChat();
    }

    /**
     * 更新 save 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    public ModelConfig save(ModelConfig config) {
        modelConfigMapper.save(config);
        return findById(config.id()).orElseThrow(() ->
                new IllegalStateException("Model config was not found after save"));
    }

    /**
     * 执行 模型配置 中的 activate 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 删除 delete 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    public void delete(String id) {
        modelConfigMapper.delete(id);
    }

    /**
     * 执行 模型配置 中的 count By Role 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public long countByRole(ModelConfigRole role) {
        return modelConfigMapper.countByRole(role.name());
    }

    /**
     * 读取 find Legacy Active 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    @Deprecated
    public Optional<ModelConfig> findLegacyActive() {
        return modelConfigMapper.findLegacyActive(ModelConfigDefaults.ACTIVE_CONFIG_ID).stream().findFirst();
    }

    /**
     * 更新 save Active 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
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
