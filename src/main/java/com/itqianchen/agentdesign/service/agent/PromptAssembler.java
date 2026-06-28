package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.enums.agent.AgentType;
import com.itqianchen.agentdesign.domain.properties.chat.ChatPromptProperties;
import org.springframework.stereotype.Service;

/**
 * Prompt Assembler 负责组装 智能体编排 提示词内容。
 * <p>模板、上下文和用户输入在这里汇合，便于统一维护提示词结构。</p>
 */
@Service
public class PromptAssembler {

    private static final String WEB_SEARCH_POLICY = """

            联网搜索工具使用规则：
            - 只有需要最新公开信息、外部事实核验，或本地知识库没有覆盖时才调用联网搜索。
            - 不要搜索或输出用户 API Key、Token、密码和隐私数据。
            - 使用网页信息回答时，优先给出可核验 URL；搜索失败时直接说明，不要编造网页来源。
            """;

    private final ChatPromptProperties promptProperties;

    /**
     * 注入聊天提示词配置。
     *
     * @param promptProperties 提示词配置属性
     */
    public PromptAssembler(ChatPromptProperties promptProperties) {
        this.promptProperties = promptProperties;
    }

    /**
     * 选择指定 Agent 的系统提示词。
     *
     * @param agentType Agent 类型
     * @return 系统提示词
     */
    public String systemPrompt(AgentType agentType) {
        return agentType == AgentType.GENERAL_CHAT
                ? promptProperties.general().system()
                : promptProperties.rag().system();
    }

    /**
     * 选择指定 Agent 的系统提示词，并按本轮能力追加联网工具规则。
     *
     * <p>未挂载联网工具时不追加规则，避免模型看到无法调用的工具约束。</p>
     *
     * @param agentType Agent 类型
     * @param webSearchEnabled 本轮是否实际挂载联网搜索工具
     * @return 系统提示词
     */
    public String systemPrompt(AgentType agentType, boolean webSearchEnabled) {
        String prompt = systemPrompt(agentType);
        return webSearchEnabled ? prompt + WEB_SEARCH_POLICY : prompt;
    }

    /**
     * 选择并填充指定 Agent 的用户提示词。
     *
     * @param agentType Agent 类型
     * @param question 用户原始问题
     * @return 用户提示词
     */
    public String userPrompt(AgentType agentType, String question) {
        String template = agentType == AgentType.GENERAL_CHAT
                ? promptProperties.general().user()
                : promptProperties.rag().user();
        return template.replace("{question}", question);
    }

    /**
     * 返回知识库无上下文时的提示词。
     *
     * @return 空上下文提示词
     */
    public String emptyContextPrompt() {
        return promptProperties.rag().emptyContext();
    }
}
