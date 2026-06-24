package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.vo.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.vo.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.enums.agent.AgentType;

/**
 * Chat 智能体 定义一个智能体执行路径。
 * <p>负责把用户问题、系统提示词、记忆和检索上下文组合成模型调用。</p>
 */
public interface ChatAgent {

    /**
     * 返回 Agent 类型。
     *
     * @return Agent 类型
     */
    AgentType type();

    /**
     * 判断当前 Agent 是否支持该请求。
     *
     * @param request Agent 请求
     * @return 是否支持
     */
    boolean supports(AgentRequest request);

    /**
     * 返回可取消的 agent 流。
     *
     * <p>实现必须负责把用户消息和助手终态写入会话，调用方只做 SSE 适配。</p>
     *
     * @param request Agent 请求
     * @return 可取消的聊天流
     */
    AgentChatStream stream(AgentRequest request);
}
