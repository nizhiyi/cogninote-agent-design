package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.domain.vo.agent.AgentRequest;
import com.itqianchen.agentdesign.domain.enums.agent.AgentType;
import com.itqianchen.agentdesign.domain.interfaces.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.enums.search.SearchMode;
import com.itqianchen.agentdesign.service.chat.ChatSessionService;
import com.itqianchen.agentdesign.service.chat.CogninoteMemoryAdvisor;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.stereotype.Service;

/**
 * Knowledge Base Chat 智能体 定义一个智能体执行路径。
 * <p>负责把用户问题、系统提示词、记忆和检索上下文组合成模型调用。</p>
 */
@Service
public class KnowledgeBaseChatAgent extends AbstractChatAgent {

    private final KnowledgeContextProvider knowledgeContextProvider;
    private final PromptAssembler promptAssembler;
    private final CogninoteMemoryAdvisor memoryAdvisor;
    private final QueryContextualizerAgent queryContextualizerAgent;

    /**
     * 注入知识库聊天 Agent 依赖。
     *
     * @param modelConfigService 模型配置服务
     * @param aiRuntimeFactory AI 运行时工厂
     * @param knowledgeContextProvider 知识库上下文提供者
     * @param promptAssembler 提示词装配器
     * @param chatSessionService 会话服务
     * @param memoryAdvisor 会话记忆 Advisor
     * @param queryContextualizerAgent 追问补全 Agent
     */
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

    /**
     * 返回知识库聊天 Agent 类型。
     *
     * @return KNOWLEDGE_BASE
     */
    @Override
    public AgentType type() {
        return AgentType.KNOWLEDGE_BASE;
    }

    /**
     * 知识库 Agent 只处理启用知识库的请求。
     *
     * @param request Agent 请求
     * @return 是否支持
     */
    @Override
    public boolean supports(AgentRequest request) {
        return request.useKnowledgeBase();
    }

    /**
     * 准备知识库聊天的检索上下文和 Advisor 链。
     *
     * <p>如果 AUTO 补全后仍无来源，会再做一次弱检索重试，尽量恢复省略追问的主题。</p>
     *
     * @param request Agent 调用上下文
     * @return 模型调用上下文
     */
    @Override
    protected AgentInvocation prepareInvocation(AgentInvocationRequest request) {
        QueryContextualization query = queryContextualizerAgent.contextualize(
                request.requestId(),
                request.conversationId(),
                request.question(),
                request.userMessage().sequence() - 1,
                request.chatConfig()
        );
        CogninoteDocumentRetriever documentRetriever = documentRetriever(request, query);
        KnowledgeContext knowledgeContext = documentRetriever.retrieveKnowledgeContext();
        if (shouldRetryWithContextualizer(query, knowledgeContext)) {
            QueryContextualization retriedQuery = queryContextualizerAgent.contextualizeForWeakRetrieval(
                    request.requestId(),
                    request.conversationId(),
                    request.question(),
                    request.userMessage().sequence() - 1,
                    request.chatConfig()
            );
            if (!retriedQuery.retrievalQuery().equals(query.retrievalQuery())) {
                query = retriedQuery;
                documentRetriever = documentRetriever(request, query);
                knowledgeContext = documentRetriever.retrieveKnowledgeContext();
            }
        }
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(memoryAdvisor);
        if (knowledgeContext.retrievalMode() != null) {
            advisors.add(RetrievalAugmentationAdvisor.builder()
                    .documentRetriever(documentRetriever)
                    .queryAugmenter(new CogninoteRagQueryAugmenter(
                            // 提示词组装是模型输入的最后一道边界，系统提示和用户提示在这里汇合。
                            promptAssembler.emptyContextPrompt(),
                            query.originalQuestion(),
                            query.retrievalQuery()
                    ))
                    .build());
        }
        return new AgentInvocation(knowledgeContext, advisors);
    }

    /**
     * 创建知识库文档检索器。
     * <p>原始问题用于最终回答，检索 query 可由补全 Agent 生成，两者必须保持分离。</p>
     *
     * @param request Agent 调用上下文
     * @param query 原始问题和检索问题
     * @return 文档检索器
     */
    private CogninoteDocumentRetriever documentRetriever(
            AgentInvocationRequest request,
            QueryContextualization query
    ) {
        return new CogninoteDocumentRetriever(
                knowledgeContextProvider,
                query.originalQuestion(),
                query.retrievalQuery(),
                request.requestedMode(),
                request.topK()
        );
    }

    /**
     * 判断是否需要在 AUTO 模式下做弱检索补全重试。
     * <p>当前弱检索只按“无来源”处理，避免因为分数阈值不稳定而重复打扰模型。</p>
     *
     * @param query 当前查询补全结果
     * @param knowledgeContext 首次检索上下文
     * @return 是否需要重试补全
     */
    private static boolean shouldRetryWithContextualizer(
            QueryContextualization query,
            KnowledgeContext knowledgeContext
    ) {
        return !query.rewritten()
                && "auto_standalone_question".equals(query.reason())
                && knowledgeContext.retrievalMode() != null
                && knowledgeContext.sources().isEmpty();
    }
}
