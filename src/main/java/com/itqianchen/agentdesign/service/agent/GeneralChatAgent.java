package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.service.chat.ChatSessionService;
import com.itqianchen.agentdesign.service.chat.CogninoteMemoryAdvisor;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * General Chat 智能体 定义一个智能体执行路径。
 * <p>负责把用户问题、系统提示词、记忆和检索上下文组合成模型调用。</p>
 */
@Service
public class GeneralChatAgent extends AbstractChatAgent {

    private final CogninoteMemoryAdvisor memoryAdvisor;

    /**
     * 注入 GeneralChatAgent 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public GeneralChatAgent(
            ModelConfigService modelConfigService,
            AiRuntimeFactory aiRuntimeFactory,
            PromptAssembler promptAssembler,
            ChatSessionService chatSessionService,
            CogninoteMemoryAdvisor memoryAdvisor
    ) {
        super(modelConfigService, aiRuntimeFactory, promptAssembler, chatSessionService);
        this.memoryAdvisor = memoryAdvisor;
    }

    /**
     * 执行 聊天会话 中的 type 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public AgentType type() {
        return AgentType.GENERAL_CHAT;
    }

    /**
     * 执行 聊天会话 中的 supports 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public boolean supports(AgentRequest request) {
        return !request.useKnowledgeBase();
    }

    /**
     * 执行 聊天会话 中的 prepare Invocation 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    protected AgentInvocation prepareInvocation(AgentInvocationRequest request) {
        return new AgentInvocation(new KnowledgeContext(null, List.of()), List.of(memoryAdvisor));
    }
}
