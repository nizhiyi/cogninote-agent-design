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
     * 注入 ChatAgentRouter 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
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
     * 执行 聊天会话 中的 route 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
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
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    public AgentChatStream stream(AgentRequest request) {
        return route(request).stream(request);
    }
}
