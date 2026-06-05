package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class CogninoteChatAgent {

    private static final Logger log = LoggerFactory.getLogger(CogninoteChatAgent.class);

    private final ModelConfigService modelConfigService;
    private final AiRuntimeFactory aiRuntimeFactory;
    private final KnowledgeContextProvider knowledgeContextProvider;
    private final PromptAssembler promptAssembler;
    private final ConversationMemoryPort conversationMemoryPort;

    public CogninoteChatAgent(
            ModelConfigService modelConfigService,
            AiRuntimeFactory aiRuntimeFactory,
            KnowledgeContextProvider knowledgeContextProvider,
            PromptAssembler promptAssembler,
            ConversationMemoryPort conversationMemoryPort
    ) {
        this.modelConfigService = modelConfigService;
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.knowledgeContextProvider = knowledgeContextProvider;
        this.promptAssembler = promptAssembler;
        this.conversationMemoryPort = conversationMemoryPort;
    }

    public AgentChatStream stream(AgentRequest request) {
        long startedAt = System.currentTimeMillis();
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? UUID.randomUUID().toString()
                : request.requestId();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();
        ModelConfig chatConfig = modelConfigService.requireActiveChatConfigured();
        String question = request.question().trim();
        int topK = normalizeTopK(request.topK(), chatConfig.resolvedDefaultTopK());
        SearchMode requestedMode = request.mode() == null ? SearchMode.HYBRID : request.mode();

        conversationMemoryPort.saveUserMessage(conversationId, question);
        KnowledgeContext knowledgeContext = knowledgeContextProvider.retrieve(question, requestedMode, topK);
        Prompt prompt = promptAssembler.assembleRagPrompt(question, knowledgeContext.contextText());

        Flux<String> answer = Flux.defer(() -> {
            StringBuilder assistantAnswer = new StringBuilder();
            return aiRuntimeFactory.chatRuntime(chatConfig)
                    .stream(prompt)
                    .doOnNext(assistantAnswer::append)
                    .doOnComplete(() -> logAgentCompleted(
                            requestId,
                            conversationId,
                            chatConfig,
                            knowledgeContext,
                            topK,
                            startedAt
                    ))
                    .doOnComplete(() -> saveAssistantMessage(conversationId, assistantAnswer))
                    .doOnError(error -> log.warn(
                            "agent_chat_failed requestId={} conversationId={} provider={} modelName={} retrievalMode={} topK={} sourceCount={} durationMs={}",
                            requestId,
                            conversationId,
                            chatConfig.provider(),
                            chatConfig.modelName(),
                            knowledgeContext.retrievalMode(),
                            topK,
                            knowledgeContext.sources().size(),
                            System.currentTimeMillis() - startedAt,
                            error
                    ));
        });

        return new AgentChatStream(
                requestId,
                conversationId,
                knowledgeContext.retrievalMode(),
                knowledgeContext.sources(),
                answer
        );
    }

    private static void logAgentCompleted(
            String requestId,
            String conversationId,
            ModelConfig chatConfig,
            KnowledgeContext knowledgeContext,
            int topK,
            long startedAt
    ) {
        log.info(
                "agent_chat_completed requestId={} conversationId={} provider={} modelName={} retrievalMode={} topK={} sourceCount={} durationMs={}",
                requestId,
                conversationId,
                chatConfig.provider(),
                chatConfig.modelName(),
                knowledgeContext.retrievalMode(),
                topK,
                knowledgeContext.sources().size(),
                System.currentTimeMillis() - startedAt
        );
    }

    private void saveAssistantMessage(String conversationId, StringBuilder assistantAnswer) {
        String content = assistantAnswer.toString();
        if (content.isBlank()) {
            return;
        }
        try {
            // 这里仍是 Noop 端口，不会写 SQLite。第十三阶段替换端口实现后，
            // 普通 SSE 断开仍会随模型流完成保存；用户显式停止会取消流，不触发 onComplete。
            conversationMemoryPort.saveAssistantMessage(conversationId, content);
        } catch (RuntimeException ex) {
            log.warn("agent_chat_memory_save_failed conversationId={}", conversationId, ex);
        }
    }

    private static int normalizeTopK(Integer requestedTopK, int configuredTopK) {
        int value = requestedTopK == null ? configuredTopK : requestedTopK;
        return Math.clamp(value, 1, 50);
    }
}
