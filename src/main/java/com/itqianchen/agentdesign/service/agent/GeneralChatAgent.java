package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.vo.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.enums.agent.AgentType;
import com.itqianchen.agentdesign.domain.interfaces.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
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
     * 注入通用聊天 Agent 依赖。
     *
     * @param modelConfigService 模型配置服务
     * @param aiRuntimeFactory AI 运行时工厂
     * @param promptAssembler 提示词装配器
     * @param chatSessionService 会话服务
     * @param memoryAdvisor 会话记忆 Advisor
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
     * 返回通用聊天 Agent 类型。
     *
     * @return GENERAL_CHAT
     */
    @Override
    public AgentType type() {
        return AgentType.GENERAL_CHAT;
    }

    /**
     * 通用聊天只处理未启用知识库的请求。
     *
     * @param request Agent 请求
     * @return 是否支持
     */
    @Override
    public boolean supports(AgentRequest request) {
        return !request.useKnowledgeBase();
    }

    /**
     * 准备通用聊天调用上下文。
     *
     * <p>通用聊天没有检索来源，只注入会话记忆。</p>
     *
     * @param request Agent 调用上下文
     * @return 模型调用上下文
     */
    @Override
    protected AgentInvocation prepareInvocation(AgentInvocationRequest request) {
        return new AgentInvocation(new KnowledgeContext(null, List.of()), List.of(memoryAdvisor));
    }
}
