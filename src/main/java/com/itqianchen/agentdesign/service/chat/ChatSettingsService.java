package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.enums.chat.QueryContextualizerMode;
import com.itqianchen.agentdesign.domain.properties.chat.QueryContextualizerProperties;
import com.itqianchen.agentdesign.domain.dto.chat.ChatSettingsRequest;
import com.itqianchen.agentdesign.domain.dto.chat.ChatSettingsResponse;
import com.itqianchen.agentdesign.repository.settings.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 聊天设置服务承载全局聊天设置的读取和保存流程。
 * <p>这里集中处理 SQLite 用户设置、环境变量和旧开关之间的优先级。</p>
 */
@Service
public class ChatSettingsService {

    private static final String QUERY_CONTEXTUALIZER_MODE_KEY = "chat.query-contextualizer.mode";

    private final AppSettingRepository appSettingRepository;
    private final QueryContextualizerProperties queryContextualizerProperties;

    /**
     * 注入聊天设置服务所需协作者。
     * <p>构造器只保存依赖，不读取数据库，避免启动阶段引入额外副作用。</p>
     *
     * @param appSettingRepository 全局设置仓储
     * @param queryContextualizerProperties 追问补全默认配置
     */
    public ChatSettingsService(
            AppSettingRepository appSettingRepository,
            QueryContextualizerProperties queryContextualizerProperties
    ) {
        this.appSettingRepository = appSettingRepository;
        this.queryContextualizerProperties = queryContextualizerProperties;
    }

    /**
     * 返回前端设置页使用的聊天设置快照。
     * <p>如果 SQLite 中没有用户设置，会先按配置兜底值写入数据库，再返回实际持久化值。</p>
     *
     * @return 聊天设置响应
     */
    @Transactional
    public ChatSettingsResponse settings() {
        return new ChatSettingsResponse(queryContextualizerMode());
    }

    /**
     * 解析当前生效的追问补全模式。
     * <p>优先级为：SQLite 用户设置、模式环境变量、旧 enabled=false、默认 AUTO。</p>
     *
     * @return 追问补全模式
     */
    @Transactional
    public QueryContextualizerMode queryContextualizerMode() {
        return appSettingRepository.findValue(QUERY_CONTEXTUALIZER_MODE_KEY)
                .map(QueryContextualizerMode::fromConfig)
                .orElseGet(this::initializeQueryContextualizerMode);
    }

    /**
     * 保存聊天设置。
     * <p>保存后立即影响后端知识库对话，不依赖浏览器 localStorage。</p>
     *
     * @param request 设置请求
     * @return 保存后的设置响应
     */
    @Transactional
    public ChatSettingsResponse update(ChatSettingsRequest request) {
        QueryContextualizerMode mode = request.queryContextualizerMode();
        appSettingRepository.save(QUERY_CONTEXTUALIZER_MODE_KEY, mode.name());
        return new ChatSettingsResponse(mode);
    }

    /**
     * 初始化追问补全模式。
     * <p>数据库缺失时只在这里落一次默认值，避免前端刷新时看到的值和后端实际执行值来自不同来源。</p>
     *
     * @return 初始化后的追问补全模式
     */
    private QueryContextualizerMode initializeQueryContextualizerMode() {
        QueryContextualizerMode mode = queryContextualizerProperties.resolvedMode();
        appSettingRepository.save(QUERY_CONTEXTUALIZER_MODE_KEY, mode.name());
        return mode;
    }
}
