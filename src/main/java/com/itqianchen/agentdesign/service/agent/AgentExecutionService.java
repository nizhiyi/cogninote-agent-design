package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;
import org.springframework.stereotype.Service;

/**
 * HTTP 流式聊天请求到具体 Agent 的执行入口。
 *
 * <p>该服务只做请求模型转换和路由，不持有聊天状态；消息落库、取消和 SSE 映射由下游服务负责。</p>
 */
@Service
public class AgentExecutionService {

    private final ChatAgentRouter chatAgentRouter;

    /**
     * 注入 Agent 路由器。
     *
     * @param chatAgentRouter 根据请求选择具体 Agent
     */
    public AgentExecutionService(ChatAgentRouter chatAgentRouter) {
        this.chatAgentRouter = chatAgentRouter;
    }

    /**
     * 将 HTTP 流式请求转换成 agent 入参，保持 Controller 不感知具体 agent 类型。
     *
     * @param request 前端流式聊天请求
     * @return Agent 聊天流
     */
    public AgentChatStream stream(ChatStreamRequest request) {
        AgentRequest agentRequest = AgentRequest.from(request);
        return chatAgentRouter.stream(agentRequest);
    }
}
