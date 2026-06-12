package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Chat 智能体 路由器 根据请求模式路由到对应的 聊天会话 实现。
 * <p>新增模式时优先在路由层扩展，不让调用方散落分支判断。</p>
 */
@Service
public class ChatAgentRouter {

    private final Map<AgentType, ChatAgent> agents;

    /**
     * 收集并校验所有 ChatAgent。
     *
     * <p>同一个 AgentType 只能注册一个实现，否则路由结果会不确定。</p>
     *
     * @param agents Spring 注入的 Agent 列表
     */
    public ChatAgentRouter(List<ChatAgent> agents) {
        EnumMap<AgentType, ChatAgent> byType = new EnumMap<>(AgentType.class);
        for (ChatAgent agent : agents) {
            ChatAgent existing = byType.put(agent.type(), agent);
            if (existing != null) {
                throw new IllegalStateException("Duplicate chat agent type: " + agent.type());
            }
        }
        this.agents = Map.copyOf(byType);
    }

    /**
     * 按请求开关选择 Agent。
     *
     * @param request Agent 请求
     * @return 具体 Agent
     */
    public ChatAgent route(AgentRequest request) {
        AgentType targetType = request.useKnowledgeBase()
                ? AgentType.KNOWLEDGE_BASE
                : AgentType.GENERAL_CHAT;
        ChatAgent agent = agents.get(targetType);
        if (agent == null) {
            throw new IllegalStateException("Chat agent is not registered: " + targetType);
        }
        return agent;
    }

    /**
     * 路由并执行 Agent。
     *
     * @param request Agent 请求
     * @return Agent 聊天流
     */
    public AgentChatStream stream(AgentRequest request) {
        return route(request).stream(request);
    }
}
