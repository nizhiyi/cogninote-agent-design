package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.service.chat.ChatSessionService;
import com.itqianchen.agentdesign.service.chat.CogninoteMemoryAdvisor;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseChatAgent extends AbstractChatAgent {

    private final KnowledgeContextProvider knowledgeContextProvider;
    private final PromptAssembler promptAssembler;
    private final CogninoteMemoryAdvisor memoryAdvisor;
    private final QueryContextualizerAgent queryContextualizerAgent;

    public KnowledgeBaseChatAgent(
            ModelConfigService modelConfigService,
            AiRuntimeFactory aiRuntimeFactory,
            KnowledgeContextProvider knowledgeContextProvider,
            PromptAssembler promptAssembler,
            ChatSessionService chatSessionService,
            CogninoteMemoryAdvisor memoryAdvisor,
            QueryContextualizerAgent queryContextualizerAgent
    ) {
        super(modelConfigService, aiRuntimeFactory, promptAssembler, chatSessionService);
        this.knowledgeContextProvider = knowledgeContextProvider;
        this.promptAssembler = promptAssembler;
        this.memoryAdvisor = memoryAdvisor;
        this.queryContextualizerAgent = queryContextualizerAgent;
    }

    @Override
    public AgentType type() {
        return AgentType.KNOWLEDGE_BASE;
    }

    @Override
    public boolean supports(AgentRequest request) {
        return request.useKnowledgeBase();
    }

    @Override
    protected AgentInvocation prepareInvocation(AgentInvocationRequest request) {
        QueryContextualization query = queryContextualizerAgent.contextualize(
                request.requestId(),
                request.conversationId(),
                request.question(),
                request.userMessage().sequence() - 1,
                request.chatConfig()
        );
        CogninoteDocumentRetriever documentRetriever = new CogninoteDocumentRetriever(
                knowledgeContextProvider,
                query.originalQuestion(),
                query.retrievalQuery(),
                request.requestedMode(),
                request.topK()
        );
        KnowledgeContext knowledgeContext = documentRetriever.retrieveKnowledgeContext();
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(memoryAdvisor);
        if (knowledgeContext.retrievalMode() != null) {
            advisors.add(RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(documentRetriever)
                    .queryAugmenter(new CogninoteRagQueryAugmenter(
                            promptAssembler.emptyContextPrompt(),
                            query.originalQuestion(),
                            query.retrievalQuery()
                    ))
                    .build());
        }
        return new AgentInvocation(knowledgeContext, advisors);
    }
}
