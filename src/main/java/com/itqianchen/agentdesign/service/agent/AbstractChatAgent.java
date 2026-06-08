package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.agent.AgentChatStream;
import com.itqianchen.agentdesign.domain.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.ChatContextUsageResponse;
import com.itqianchen.agentdesign.service.chat.ChatSessionService;
import com.itqianchen.agentdesign.service.chat.CogninoteMemoryAdvisor;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

/**
 * Abstract Chat 智能体 定义一个智能体执行路径。
 * <p>负责把用户问题、系统提示词、记忆和检索上下文组合成模型调用。</p>
 */
public abstract class AbstractChatAgent implements ChatAgent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ModelConfigService modelConfigService;
    private final AiRuntimeFactory aiRuntimeFactory;
    private final PromptAssembler promptAssembler;
    private final ChatSessionService chatSessionService;

    /**
     * 注入 AbstractChatAgent 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    protected AbstractChatAgent(
            ModelConfigService modelConfigService,
            AiRuntimeFactory aiRuntimeFactory,
            PromptAssembler promptAssembler,
            ChatSessionService chatSessionService
    ) {
        this.modelConfigService = modelConfigService;
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.promptAssembler = promptAssembler;
        this.chatSessionService = chatSessionService;
    }

    /**
     * 启动 stream 流式流程。
     * <p>方法串联请求准备、事件流返回和结束后的状态收尾。</p>
     */
    @Override
    public final AgentChatStream stream(AgentRequest request) {
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
        boolean useKnowledgeBase = type() == AgentType.KNOWLEDGE_BASE;

        chatSessionService.ensureSession(conversationId, question, useKnowledgeBase, requestedMode, topK);
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatMessage userMessage = chatSessionService.appendUserMessage(
                conversationId,
                question,
                requestId,
                type(),
                useKnowledgeBase,
                requestedMode,
                topK
        );
        AgentInvocation invocation = prepareInvocation(new AgentInvocationRequest(
                requestId,
                conversationId,
                question,
                requestedMode,
                topK,
                userMessage,
                chatConfig
        ));
        KnowledgeContext knowledgeContext = invocation.knowledgeContext();
        ChatContextUsageResponse initialContextUsage = chatSessionService.contextUsage(conversationId);
        Map<String, Object> advisorParams = Map.of(
                ChatMemory.CONVERSATION_ID, conversationId,
                CogninoteMemoryAdvisor.MAX_MESSAGE_SEQUENCE, userMessage.sequence() - 1,
                /**
                 * 执行 聊天会话 中的 type 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                CogninoteMemoryAdvisor.AGENT_TYPE, type()
        );
        StringBuilder assistantAnswer = new StringBuilder();
        AtomicBoolean saved = new AtomicBoolean(false);
        // 这里开始真正的模型对话调用，后续 Flux 事件会驱动前端流式展示。
        Flux<String> answer = Flux.defer(() -> aiRuntimeFactory.chatRuntime(chatConfig)
                .stream(
                        // 提示词组装是模型输入的最后一道边界，系统提示和用户提示在这里汇合。
                        promptAssembler.systemPrompt(type()),
                        // 提示词组装是模型输入的最后一道边界，系统提示和用户提示在这里汇合。
                        promptAssembler.userPrompt(type(), question),
                        invocation.advisors(),
                        advisorParams
                )
                .doOnNext(assistantAnswer::append)
                .doOnComplete(() -> logAgentCompleted(
                        requestId,
                        conversationId,
                        chatConfig,
                        knowledgeContext,
                        topK,
                        startedAt
                ))
                .doOnError(error -> log.warn(
                        "agent_chat_failed requestId={} conversationId={} agentType={} provider={} modelName={} retrievalMode={} topK={} sourceCount={} durationMs={}",
                        requestId,
                        conversationId,
                        type(),
                        chatConfig.provider(),
                        chatConfig.modelName(),
                        knowledgeContext.retrievalMode(),
                        topK,
                        knowledgeContext.sources().size(),
                        System.currentTimeMillis() - startedAt,
                        error
                ))
                .doOnComplete(() -> saveAssistantDone(
                        saved,
                        conversationId,
                        assistantAnswer.toString(),
                        requestId,
                        knowledgeContext
                ))
                .doOnError(error -> saveAssistantError(
                        saved,
                        conversationId,
                        assistantAnswer.toString(),
                        requestId,
                        knowledgeContext
                )));

        return new AgentChatStream(
                requestId,
                conversationId,
                knowledgeContext.retrievalMode(),
                knowledgeContext.sources(),
                initialContextUsage,
                () -> chatSessionService.contextUsage(conversationId),
                answer,
                () -> saveAssistantStopped(
                        saved,
                        conversationId,
                        assistantAnswer.toString(),
                        requestId,
                        knowledgeContext
                )
        );
    }

    /**
     * 执行 聊天会话 中的 prepare Invocation 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    protected abstract AgentInvocation prepareInvocation(AgentInvocationRequest request);

    /**
     * 智能体 Invocation 请求 定义 聊天会话 接口允许接收的请求字段。
     * <p>字段校验应和前端表单、接口文档保持一致。</p>
     */
    protected record AgentInvocationRequest(
            String requestId,
            String conversationId,
            String question,
            SearchMode requestedMode,
            int topK,
            ChatMessage userMessage,
            ModelConfig chatConfig
    ) {
    }

    /**
     * 智能体 Invocation 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    protected record AgentInvocation(KnowledgeContext knowledgeContext, List<Advisor> advisors) {
    }

    /**
     * 执行 聊天会话 中的 log 智能体 Completed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void logAgentCompleted(
            String requestId,
            String conversationId,
            ModelConfig chatConfig,
            KnowledgeContext knowledgeContext,
            int topK,
            long startedAt
    ) {
        log.info(
                "agent_chat_completed requestId={} conversationId={} agentType={} provider={} modelName={} retrievalMode={} topK={} sourceCount={} durationMs={}",
                requestId,
                conversationId,
                type(),
                chatConfig.provider(),
                chatConfig.modelName(),
                knowledgeContext.retrievalMode(),
                topK,
                knowledgeContext.sources().size(),
                System.currentTimeMillis() - startedAt
        );
    }

    /**
     * 更新 save Assistant Done 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    private void saveAssistantDone(
            AtomicBoolean saved,
            String conversationId,
            String content,
            String requestId,
            KnowledgeContext knowledgeContext
    ) {
        if (content.isBlank() || !saved.compareAndSet(false, true)) {
            return;
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionService.appendAssistantDone(
                conversationId,
                content,
                requestId,
                type(),
                knowledgeContext.retrievalMode(),
                knowledgeContext.sources()
        );
    }

    /**
     * 更新 save Assistant Stopped 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    private void saveAssistantStopped(
            AtomicBoolean saved,
            String conversationId,
            String content,
            String requestId,
            KnowledgeContext knowledgeContext
    ) {
        if (content.isBlank() || !saved.compareAndSet(false, true)) {
            return;
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionService.appendAssistantStopped(
                conversationId,
                content,
                requestId,
                type(),
                knowledgeContext.retrievalMode(),
                knowledgeContext.sources()
        );
    }

    /**
     * 更新 save Assistant Error 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    private void saveAssistantError(
            AtomicBoolean saved,
            String conversationId,
            String content,
            String requestId,
            KnowledgeContext knowledgeContext
    ) {
        if (content.isBlank() || !saved.compareAndSet(false, true)) {
            return;
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionService.appendAssistantError(
                conversationId,
                content,
                requestId,
                type(),
                knowledgeContext.retrievalMode(),
                knowledgeContext.sources()
        );
    }

    /**
     * 规范化 Top K 输入。
     * <p>后续逻辑只处理受控取值，减少重复分支和边界判断。</p>
     */
    private static int normalizeTopK(Integer requestedTopK, int configuredTopK) {
        int value = requestedTopK == null ? configuredTopK : requestedTopK;
        return Math.clamp(value, 1, 50);
    }
}
