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
