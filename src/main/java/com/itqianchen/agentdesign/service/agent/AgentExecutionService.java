package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.dto.chat.ChatStreamRequest;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionService {

    private final CogninoteChatAgent cogninoteChatAgent;

    public AgentExecutionService(CogninoteChatAgent cogninoteChatAgent) {
        this.cogninoteChatAgent = cogninoteChatAgent;
    }

    public AgentChatStream stream(ChatStreamRequest request) {
        return cogninoteChatAgent.stream(AgentRequest.from(request));
    }
}
