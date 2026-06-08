package com.itqianchen.agentdesign.service.model;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelConfigDefaults;
import com.itqianchen.agentdesign.domain.model.ModelConfigRole;
import com.itqianchen.agentdesign.domain.model.ModelConfigurationException;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import com.itqianchen.agentdesign.dto.model.ActiveModelConfigsResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigRequest;
import com.itqianchen.agentdesign.dto.model.ModelConfigResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigSettingsResponse;
import com.itqianchen.agentdesign.dto.model.ModelConfigUpsertRequest;
import com.itqianchen.agentdesign.repository.model.ModelConfigRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model 配置 服务 承载 模型配置 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;

    /**
     * 注入 ModelConfigService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ModelConfigService(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    /**
     * 查询 模型配置 列表。
     * <p>返回值面向上层展示或接口响应，不暴露底层存储细节。</p>
     */
    public List<ModelConfig> list(ModelConfigRole role) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.findAll(role).stream()
                .map(ModelConfigService::normalizeLoadedConfig)
                .toList();
    }

    /**
     * 构建模型设置页使用的配置快照。
     * <p>快照同时包含列表、选中项和当前激活项，便于前端一次性渲染。</p>
     */
    @Transactional
    public ModelConfigSettingsResponse settingsSnapshot(ModelConfigRole role) {
        /**
         * 确保 ensure Role Has Active 配置 所需前置条件存在。
         * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
         */
        ensureRoleHasActiveConfig(ModelConfigRole.CHAT);
        /**
         * 确保 ensure Role Has Active 配置 所需前置条件存在。
         * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
         */
        ensureRoleHasActiveConfig(ModelConfigRole.EMBEDDING);
        return settingsSnapshot(role, Optional.empty());
    }

    /**
     * 读取 active Chat Or Default 的最终值。
     * <p>当调用方没有显式配置时，返回当前模块约定的默认值。</p>
     */
    public ModelConfig activeChatOrDefault() {
        return activeOrDefault(ModelConfigRole.CHAT);
    }

    /**
     * 读取 active Embedding Or Default 的最终值。
     * <p>当调用方没有显式配置时，返回当前模块约定的默认值。</p>
     */
    public ModelConfig activeEmbeddingOrDefault() {
        return activeOrDefault(ModelConfigRole.EMBEDDING);
    }

    /**
     * 读取 active Or Default 的最终值。
     * <p>当调用方没有显式配置时，返回当前模块约定的默认值。</p>
     */
    public ModelConfig activeOrDefault(ModelConfigRole role) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.findActive(role)
                .map(ModelConfigService::normalizeLoadedConfig)
                .orElseGet(() -> defaultConfig(role, true));
    }

    /**
     * 读取 active Or Default 的最终值。
     * <p>当调用方没有显式配置时，返回当前模块约定的默认值。</p>
     */
    @Deprecated
    public ModelConfig activeOrDefault() {
        return activeChatOrDefault();
    }

    /**
     * 读取必需的 require Active Chat Configured 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    public ModelConfig requireActiveChatConfigured() {
        return requireConfigured(ModelConfigRole.CHAT);
    }

    /**
     * 读取必需的 require Active Embedding Configured 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    public ModelConfig requireActiveEmbeddingConfigured() {
        return requireConfigured(ModelConfigRole.EMBEDDING);
    }

    /**
     * 读取必需的 require Configured 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    @Deprecated
    public ModelConfig requireConfigured() {
        return requireActiveChatConfigured();
    }

    /**
     * 读取必需的 require Configured 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    public ModelConfig requireConfigured(ModelConfigRole role) {
        ModelConfig config = activeOrDefault(role);
        if (!config.hasApiKey()) {
            throw new ModelConfigurationException(roleLabel(role) + " API Key is not configured");
        }
        return config;
    }

    /**
     * 创建 create 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @Transactional
    public ModelConfig create(ModelConfigRequest request) {
        ModelConfigRole role = normalizeRole(request.role());
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        boolean active = modelConfigRepository.countByRole(role) == 0;
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.save(mergeRequest(
                request,
                defaultConfig(role, active),
                UUID.randomUUID().toString(),
                role,
                active,
                now
        ));
    }

    /**
     * 创建 create Settings 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @Transactional
    public ModelConfigSettingsResponse createSettings(ModelConfigUpsertRequest request) {
        ModelConfig created = create(request.toModelConfigRequest());
        return settingsSnapshot(created.role(), Optional.of(created.id()));
    }

    /**
     * 更新 update 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Transactional
    public ModelConfig update(String id, ModelConfigRequest request) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ModelConfig existing = modelConfigRepository.findById(id)
                .orElseThrow(() -> new ModelConfigurationException("Model config not found: " + id));
        ModelConfigRole requestedRole = request.role() == null || request.role().isBlank()
                ? existing.role()
                : normalizeRole(request.role());
        if (requestedRole != existing.role()) {
            throw new ModelConfigurationException("Model config role cannot be changed");
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.save(mergeRequest(
                request,
                existing,
                existing.id(),
                existing.role(),
                existing.active(),
                System.currentTimeMillis()
        ));
    }

    /**
     * 更新 update Settings 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Transactional
    public ModelConfigSettingsResponse updateSettings(String id, ModelConfigUpsertRequest request) {
        ModelConfig updated = update(id, request.toModelConfigRequest());
        return settingsSnapshot(updated.role(), Optional.of(updated.id()));
    }

    /**
     * 执行 模型配置 中的 activate 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Transactional
    public ModelConfig activate(String id) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return modelConfigRepository.activate(id, System.currentTimeMillis());
    }

    /**
     * 执行 模型配置 中的 activate Settings 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Transactional
    public ModelConfigSettingsResponse activateSettings(String id) {
        ModelConfig activated = activate(id);
        return settingsSnapshot(activated.role(), Optional.of(activated.id()));
    }

    /**
     * 删除 delete 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Transactional
    public void delete(String id) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new ModelConfigurationException("Model config not found: " + id));
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        modelConfigRepository.delete(id);
        /**
         * 确保 ensure Role Has Active 配置 所需前置条件存在。
         * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
         */
        ensureRoleHasActiveConfig(config.role());
    }

    /**
     * 删除 delete Settings 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Transactional
    public ModelConfigSettingsResponse deleteSettings(String id) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ModelConfig config = modelConfigRepository.findById(id)
                .orElseThrow(() -> new ModelConfigurationException("Model config not found: " + id));
        /**
         * 删除 delete 对应的数据。
         * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
         */
        delete(id);
        return settingsSnapshot(config.role(), Optional.empty());
    }

    /**
     * 确保 ensure Role Has Active 配置 所需前置条件存在。
     * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
     */
    private void ensureRoleHasActiveConfig(ModelConfigRole role) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        List<ModelConfig> remaining = modelConfigRepository.findAll(role);
        if (remaining.isEmpty()) {
            // 模型设置页不能进入“无配置、无 active”的坏状态。删除某个角色最后一条配置时，
            // 立即补一条默认 active 草稿，让前端永远能拿到可显示的 selectedConfig。
            modelConfigRepository.save(defaultConfig(role, true));
            return;
        }
        if (remaining.stream().noneMatch(ModelConfig::active)) {
            // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
            modelConfigRepository.activate(remaining.getFirst().id(), System.currentTimeMillis());
        }
    }

    /**
     * 执行 模型配置 中的 connection 测试 配置 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ModelConfig connectionTestConfig(ModelConfigRequest request) {
        ModelConfigRole role = normalizeRole(request.role());
        ModelConfig config = mergeRequest(
                request,
                activeOrDefault(role),
                activeOrDefault(role).id(),
                role,
                true,
                System.currentTimeMillis()
        );
        if (!config.hasApiKey()) {
            throw new ModelConfigurationException(roleLabel(role) + " API Key is required for connection test");
        }
        return config;
    }

    /**
     * 更新 save 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Transactional
    @Deprecated
    public ModelConfig save(ModelConfigRequest request) {
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ModelConfig chat = modelConfigRepository.save(mergeLegacyChatRequest(request, activeChatOrDefault(), now));
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        modelConfigRepository.save(mergeLegacyEmbeddingRequest(request, activeEmbeddingOrDefault(), now));
        return chat;
    }

    /**
     * 构建模型设置页使用的配置快照。
     * <p>快照同时包含列表、选中项和当前激活项，便于前端一次性渲染。</p>
     */
    private ModelConfigSettingsResponse settingsSnapshot(ModelConfigRole role, Optional<String> preferredSelectedId) {
        List<ModelConfig> configs = list(role);
        ModelConfig selected = preferredSelectedId
                .flatMap(id -> configs.stream().filter(config -> config.id().equals(id)).findFirst())
                .or(() -> configs.stream().filter(ModelConfig::active).findFirst())
                .or(() -> configs.stream().findFirst())
                .orElse(null);
        return new ModelConfigSettingsResponse(
                activeConfigsResponse(),
                role.name(),
                configs.stream().map(ModelConfigResponse::from).toList(),
                selected == null ? null : ModelConfigResponse.from(selected)
        );
    }

    /**
     * 执行 模型配置 中的 active Configs 响应 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private ActiveModelConfigsResponse activeConfigsResponse() {
        return new ActiveModelConfigsResponse(
                ModelConfigResponse.from(activeChatOrDefault()),
                ModelConfigResponse.from(activeEmbeddingOrDefault())
        );
    }

    /**
     * 执行 模型配置 中的 merge 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfig mergeRequest(
            ModelConfigRequest request,
            ModelConfig existing,
            String id,
            ModelConfigRole role,
            boolean active,
            long now
    ) {
        ModelProvider provider = normalizeProvider(request.provider());
        String requestedApiKey = normalizeApiKey(request.apiKey());
        // API Key 允许留空复用旧值。否则用户只改模型 ID 时会被迫重复粘贴密钥。
        String apiKey = requestedApiKey.isBlank() ? existing.apiKey() : requestedApiKey;
        String modelName = normalizeModelName(role, request, existing.modelName());
        return new ModelConfig(
                id,
                role,
                provider,
                normalizeDisplayName(role, request.displayName()),
                normalizeBaseUrl(provider, request.baseUrl()),
                apiKey,
                modelName,
                role == ModelConfigRole.EMBEDDING ? ModelConfigDefaults.EMBEDDING_DIMENSIONS : null,
                role == ModelConfigRole.CHAT ? normalizeTemperature(request.temperature(), existing) : null,
                role == ModelConfigRole.CHAT ? normalizeTopK(request.defaultTopK(), request.topK(), existing) : null,
                role == ModelConfigRole.CHAT ? normalizeContextWindowTokens(request.contextWindowTokens(), existing) : null,
                active,
                existing.createdAt(),
                now
        );
    }

    /**
     * 执行 模型配置 中的 merge Legacy Chat 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfig mergeLegacyChatRequest(ModelConfigRequest request, ModelConfig existing, long now) {
        return mergeRequest(
                new ModelConfigRequest(
                        ModelConfigRole.CHAT.name(),
                        request.provider(),
                        request.displayName(),
                        request.baseUrl(),
                        request.apiKey(),
                        request.chatModel(),
                        request.chatModel(),
                        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
                        request.embeddingModel(),
                        null,
                        request.temperature(),
                        request.topK(),
                        request.defaultTopK(),
                        request.contextWindowTokens()
                ),
                existing,
                existing.id(),
                ModelConfigRole.CHAT,
                true,
                now
        );
    }

    /**
     * 执行 模型配置 中的 merge Legacy Embedding 请求 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfig mergeLegacyEmbeddingRequest(ModelConfigRequest request, ModelConfig existing, long now) {
        return mergeRequest(
                new ModelConfigRequest(
                        ModelConfigRole.EMBEDDING.name(),
                        request.provider(),
                        request.displayName(),
                        request.baseUrl(),
                        request.apiKey(),
                        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
                        request.embeddingModel(),
                        request.chatModel(),
                        // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
                        request.embeddingModel(),
                        request.embeddingDimensions(),
                        null,
                        null,
                        null,
                        null
                ),
                existing,
                existing.id(),
                ModelConfigRole.EMBEDDING,
                true,
                now
        );
    }

    /**
     * 执行 模型配置 中的 default 配置 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static ModelConfig defaultConfig(ModelConfigRole role, boolean active) {
        long now = System.currentTimeMillis();
        return new ModelConfig(
                role == ModelConfigRole.CHAT
                        ? ModelConfigDefaults.ACTIVE_CHAT_CONFIG_ID
                        : ModelConfigDefaults.ACTIVE_EMBEDDING_CONFIG_ID,
                role,
                ModelConfigDefaults.PROVIDER,
                role == ModelConfigRole.CHAT
                        ? ModelConfigDefaults.CHAT_DISPLAY_NAME
                        : ModelConfigDefaults.EMBEDDING_DISPLAY_NAME,
                ModelConfigDefaults.BASE_URL,
                "",
                role == ModelConfigRole.CHAT
                        ? ModelConfigDefaults.CHAT_MODEL
                        : ModelConfigDefaults.EMBEDDING_MODEL,
                role == ModelConfigRole.EMBEDDING ? ModelConfigDefaults.EMBEDDING_DIMENSIONS : null,
                role == ModelConfigRole.CHAT ? ModelConfigDefaults.TEMPERATURE : null,
                role == ModelConfigRole.CHAT ? ModelConfigDefaults.TOP_K : null,
                role == ModelConfigRole.CHAT ? ModelConfigDefaults.CONTEXT_WINDOW_TOKENS : null,
                active,
                now,
                now
        );
    }

    /**
     * 规范化 normalize Loaded 配置 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static ModelConfig normalizeLoadedConfig(ModelConfig config) {
        if (config.provider() != ModelProvider.DASHSCOPE) {
            return config;
        }
        String normalizedBaseUrl = DashScopeBaseUrls.normalizeConfigBaseUrl(config.baseUrl());
        if (normalizedBaseUrl.equals(config.baseUrl())) {
            return config;
        }
        return new ModelConfig(
                config.id(),
                config.role(),
                config.provider(),
                config.displayName(),
                normalizedBaseUrl,
                config.apiKey(),
                config.modelName(),
                config.embeddingDimensions(),
                config.temperature(),
                config.defaultTopK(),
                config.contextWindowTokens(),
                config.active(),
                config.createdAt(),
                config.updatedAt()
        );
    }

    /**
     * 规范化消息角色输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static ModelConfigRole normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return ModelConfigRole.CHAT;
        }
        try {
            return ModelConfigRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ModelConfigurationException("Unsupported model config role: " + role);
        }
    }

    /**
     * 规范化 normalize Provider 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static ModelProvider normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return ModelConfigDefaults.PROVIDER;
        }
        try {
            return ModelProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ModelConfigurationException("Unsupported model provider: " + provider);
        }
    }

    /**
     * 规范化 normalize Display Name 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static String normalizeDisplayName(ModelConfigRole role, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return role == ModelConfigRole.CHAT
                    ? ModelConfigDefaults.CHAT_DISPLAY_NAME
                    : ModelConfigDefaults.EMBEDDING_DISPLAY_NAME;
        }
        return displayName.trim();
    }

    /**
     * 规范化 normalize Base Url 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static String normalizeBaseUrl(ModelProvider provider, String baseUrl) {
        return switch (provider) {
            case DASHSCOPE -> DashScopeBaseUrls.normalizeConfigBaseUrl(ModelConfigDefaults.BASE_URL);
            case OPENAI_COMPATIBLE -> OpenAiCompatibleUrls.normalizeBaseUrl(baseUrl);
        };
    }

    /**
     * 规范化 normalize Api Key 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static String normalizeApiKey(String apiKey) {
        return apiKey == null ? "" : apiKey.trim();
    }

    /**
     * 规范化 normalize Model Name 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static String normalizeModelName(ModelConfigRole role, ModelConfigRequest request, String fallback) {
        String value = request.modelName();
        if ((value == null || value.isBlank()) && role == ModelConfigRole.CHAT) {
            value = request.chatModel();
        }
        if ((value == null || value.isBlank()) && role == ModelConfigRole.EMBEDDING) {
            // 向量模型调用可能受网络和模型配置影响，异常会交给上层统一处理。
            value = request.embeddingModel();
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        if (value == null || value.isBlank()) {
            throw new ModelConfigurationException(roleLabel(role) + " model name is required");
        }
        return value.trim();
    }

    /**
     * 规范化 normalize Temperature 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static Double normalizeTemperature(Double requested, ModelConfig existing) {
        return requested == null ? existing.resolvedTemperature() : requested;
    }

    /**
     * 规范化 Top K 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static Integer normalizeTopK(Integer defaultTopK, Integer topK, ModelConfig existing) {
        if (defaultTopK != null) {
            return defaultTopK;
        }
        if (topK != null) {
            return topK;
        }
        return existing.resolvedDefaultTopK();
    }

    /**
     * 规范化 normalize Context Window Tokens 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static Integer normalizeContextWindowTokens(Integer requested, ModelConfig existing) {
        int value = requested == null ? existing.resolvedContextWindowTokens() : requested;
        return Math.clamp(
                value,
                ModelConfigDefaults.MIN_CONTEXT_WINDOW_TOKENS,
                ModelConfigDefaults.MAX_CONTEXT_WINDOW_TOKENS
        );
    }

    /**
     * 执行 模型配置 中的 role Label 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String roleLabel(ModelConfigRole role) {
        return role == ModelConfigRole.CHAT ? "Chat" : "Embedding";
    }
}
