package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;
import org.springframework.stereotype.Service;

/**
 * 智能体 Execution 服务 承载 智能体编排 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class AgentExecutionService {

    private final ChatAgentRouter chatAgentRouter;

    /**
     * 注入 AgentExecutionService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public AgentExecutionService(ChatAgentRouter chatAgentRouter) {
        this.chatAgentRouter = chatAgentRouter;
    }

    /**
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    public AgentChatStream stream(ChatStreamRequest request) {
        AgentRequest agentRequest = AgentRequest.from(request);
        return chatAgentRouter.stream(agentRequest);
    }
}
