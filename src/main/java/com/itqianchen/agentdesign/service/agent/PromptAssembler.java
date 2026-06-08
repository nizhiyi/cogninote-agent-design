package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import org.springframework.stereotype.Service;

/**
 * Prompt Assembler 负责组装 智能体编排 提示词内容。
 * <p>模板、上下文和用户输入在这里汇合，便于统一维护提示词结构。</p>
 */
@Service
public class PromptAssembler {

    private final ChatPromptProperties promptProperties;

    /**
     * 注入 PromptAssembler 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public PromptAssembler(ChatPromptProperties promptProperties) {
        this.promptProperties = promptProperties;
    }

    /**
     * 执行 智能体编排 中的 system Prompt 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String systemPrompt(AgentType agentType) {
        return agentType == AgentType.GENERAL_CHAT
                ? promptProperties.general().system()
                : promptProperties.rag().system();
    }

    /**
     * 执行 智能体编排 中的 user Prompt 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String userPrompt(AgentType agentType, String question) {
        String template = agentType == AgentType.GENERAL_CHAT
                ? promptProperties.general().user()
                : promptProperties.rag().user();
        return template.replace("{question}", question);
    }

    /**
     * 执行 智能体编排 中的 empty Context Prompt 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String emptyContextPrompt() {
        return promptProperties.rag().emptyContext();
    }
}
