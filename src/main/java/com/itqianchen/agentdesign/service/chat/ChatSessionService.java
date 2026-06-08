package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.chat.ChatMemoryProperties;
import com.itqianchen.agentdesign.domain.chat.ChatMessage;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.chat.ChatMessageStatus;
import com.itqianchen.agentdesign.domain.chat.ChatSession;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.dto.chat.ChatMessageResponse;
import com.itqianchen.agentdesign.dto.chat.ChatContextUsageResponse;
import com.itqianchen.agentdesign.dto.chat.ChatSessionCreateRequest;
import com.itqianchen.agentdesign.dto.chat.ChatSessionResponse;
import com.itqianchen.agentdesign.dto.chat.ChatSessionUpdateRequest;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import com.itqianchen.agentdesign.repository.chat.ChatSessionRepository;
import com.itqianchen.agentdesign.service.model.ModelConfigService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat Session 服务 承载 聊天会话 的应用服务流程。
 * <p>这里集中编排仓储、模型运行时和 DTO 映射，保证控制器保持轻量。</p>
 */
@Service
public class ChatSessionService {

    private static final int SUMMARY_MAX_CHARS = 4000;

    private final ChatSessionRepository chatSessionRepository;
    private final RagSourcesJsonCodec ragSourcesJsonCodec;
    private final TokenEstimator tokenEstimator;
    private final ChatMemoryProperties memoryProperties;
    private final ChatContextUsageService contextUsageService;
    private final ModelConfigService modelConfigService;

    /**
     * 注入 ChatSessionService 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public ChatSessionService(
            ChatSessionRepository chatSessionRepository,
            RagSourcesJsonCodec ragSourcesJsonCodec,
            TokenEstimator tokenEstimator,
            ChatMemoryProperties memoryProperties,
            ChatContextUsageService contextUsageService,
            ModelConfigService modelConfigService
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.ragSourcesJsonCodec = ragSourcesJsonCodec;
        this.tokenEstimator = tokenEstimator;
        this.memoryProperties = memoryProperties;
        this.contextUsageService = contextUsageService;
        this.modelConfigService = modelConfigService;
    }

    /**
     * 查询 聊天会话 列表。
     * <p>返回值面向上层展示或接口响应，不暴露底层存储细节。</p>
     */
    public List<ChatSessionResponse> listSessions() {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return chatSessionRepository.findActiveSessionSummaries().stream()
                .map(this::withContextUsage)
                .toList();
    }

    /**
     * 创建 create Session 对应的数据。
     * <p>创建流程集中处理默认值、校验和持久化边界。</p>
     */
    @Transactional
    public ChatSessionResponse createSession(ChatSessionCreateRequest request) {
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession session = chatSessionRepository.create(
                request == null ? null : request.title(),
                request == null || request.useKnowledgeBase() == null || request.useKnowledgeBase(),
                request == null ? SearchMode.HYBRID : request.mode(),
                request == null || request.topK() == null ? 8 : request.topK(),
                now
        );
        return withContextUsage(ChatSessionResponse.from(session, List.of()));
    }

    /**
     * 读取 get Session 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    public ChatSessionResponse getSession(String conversationId) {
        ChatSession session = requireSession(conversationId);
        return withContextUsage(ChatSessionResponse.from(session, messageResponses(conversationId)));
    }

    /**
     * 更新 update Session 对应的数据。
     * <p>方法负责保持内存快照、数据库记录和返回值语义一致。</p>
     */
    @Transactional
    public ChatSessionResponse updateSession(String conversationId, ChatSessionUpdateRequest request) {
        ChatSession existing = requireSession(conversationId);
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.updateOptions(
                conversationId,
                request.title(),
                request.useKnowledgeBase() == null ? existing.useKnowledgeBase() : request.useKnowledgeBase(),
                request.mode() == null ? existing.retrievalMode() : request.mode(),
                request.topK() == null ? existing.topK() : request.topK(),
                System.currentTimeMillis()
        );
        return getSession(conversationId);
    }

    /**
     * 删除 delete Session 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @Transactional
    public void deleteSession(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        if (!chatSessionRepository.deleteSession(conversationId)) {
            throw new ResourceNotFoundException("Chat session not found: " + conversationId);
        }
    }

    /**
     * 清理 clear Messages 对应的数据。
     * <p>清理只移除目标内容，保留会话或模块继续运行所需的外壳状态。</p>
     */
    @Transactional
    public ChatSessionResponse clearMessages(String conversationId) {
        /**
         * 读取必需的 require Session 配置或数据。
         * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
         */
        requireSession(conversationId);
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.clearMessages(conversationId, System.currentTimeMillis());
        return getSession(conversationId);
    }

    /**
     * 确保 ensure Session 所需前置条件存在。
     * <p>不存在时创建默认资源或抛出明确异常，避免后续流程隐式失败。</p>
     */
    @Transactional
    public ChatSession ensureSession(
            String conversationId,
            String fallbackTitle,
            boolean useKnowledgeBase,
            SearchMode mode,
            int topK
    ) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return chatSessionRepository.ensureSession(
                conversationId,
                fallbackTitle,
                useKnowledgeBase,
                mode,
                topK,
                System.currentTimeMillis()
        );
    }

    /**
     * 追加 append User Message 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    @Transactional
    public ChatMessage appendUserMessage(
            String conversationId,
            String content,
            String requestId,
            AgentType agentType,
            boolean useKnowledgeBase,
            SearchMode mode,
            int topK
    ) {
        long now = System.currentTimeMillis();
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession session = chatSessionRepository.ensureSession(
                conversationId,
                titleFromQuestion(content),
                useKnowledgeBase,
                mode,
                topK,
                now
        );
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        if ("新对话".equals(session.title()) && chatSessionRepository.countMessages(session.id()) == 0) {
            // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
            chatSessionRepository.updateOptions(
                    session.id(),
                    titleFromQuestion(content),
                    useKnowledgeBase,
                    mode,
                    topK,
                    now
            );
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return chatSessionRepository.appendMessage(
                session.id(),
                ChatMessageRole.USER,
                content,
                ChatMessageStatus.DONE,
                requestId,
                agentType,
                null,
                null,
                estimateChatMessage(content),
                now
        );
    }

    /**
     * 追加 append Assistant Done 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    @Transactional
    public void appendAssistantDone(
            String conversationId,
            String content,
            String requestId,
            AgentType agentType,
            SearchMode retrievalMode,
            List<RagSourceResponse> sources
    ) {
        /**
         * 追加 append Assistant 数据。
         * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
         */
        appendAssistant(conversationId, content, requestId, agentType, retrievalMode, sources, ChatMessageStatus.DONE);
        /**
         * 执行 聊天会话 中的 refresh Summary If Needed 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        refreshSummaryIfNeeded(conversationId);
    }

    /**
     * 追加 append Assistant Stopped 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    @Transactional
    public void appendAssistantStopped(
            String conversationId,
            String content,
            String requestId,
            AgentType agentType,
            SearchMode retrievalMode,
            List<RagSourceResponse> sources
    ) {
        /**
         * 追加 append Assistant 数据。
         * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
         */
        appendAssistant(conversationId, content, requestId, agentType, retrievalMode, sources, ChatMessageStatus.STOPPED);
        /**
         * 执行 聊天会话 中的 refresh Summary If Needed 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        refreshSummaryIfNeeded(conversationId);
    }

    /**
     * 追加 append Assistant Error 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    @Transactional
    public void appendAssistantError(
            String conversationId,
            String content,
            String requestId,
            AgentType agentType,
            SearchMode retrievalMode,
            List<RagSourceResponse> sources
    ) {
        /**
         * 追加 append Assistant 数据。
         * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
         */
        appendAssistant(conversationId, content, requestId, agentType, retrievalMode, sources, ChatMessageStatus.ERROR);
    }

    /**
     * 读取必需的 require Session 配置或数据。
     * <p>缺失时立即失败，避免外部模型或数据库调用才暴露问题。</p>
     */
    public ChatSession requireSession(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return chatSessionRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + conversationId));
    }

    /**
     * 追加 append Assistant 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    private void appendAssistant(
            String conversationId,
            String content,
            String requestId,
            AgentType agentType,
            SearchMode retrievalMode,
            List<RagSourceResponse> sources,
            ChatMessageStatus status
    ) {
        if (content == null || content.isBlank()) {
            return;
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        chatSessionRepository.appendMessage(
                conversationId,
                ChatMessageRole.ASSISTANT,
                content,
                status,
                requestId,
                agentType,
                retrievalMode,
                ragSourcesJsonCodec.encode(sources),
                estimateChatMessage(content),
                System.currentTimeMillis()
        );
    }

    /**
     * 执行 聊天会话 中的 message Responses 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<ChatMessageResponse> messageResponses(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        return chatSessionRepository.findMessages(conversationId).stream()
                .map(message -> ChatMessageResponse.from(message, ragSourcesJsonCodec.decode(message.sourcesJson())))
                .toList();
    }

    /**
     * 执行 聊天会话 中的 refresh Summary If Needed 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void refreshSummaryIfNeeded(String conversationId) {
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        ChatSession session = chatSessionRepository.findById(conversationId).orElse(null);
        if (session == null) {
            return;
        }
        // 写入会影响本地 SQLite 状态，调用顺序需要和会话状态机保持一致。
        List<ChatMessage> messages = chatSessionRepository.findMessages(conversationId);
        if (!contextUsageService.shouldSummarize(session, messages)) {
            return;
        }

        int protectedRecentMessages = memoryProperties.resolvedMinimumRecentMessages();
        int coverUntilIndex = Math.max(0, messages.size() - protectedRecentMessages);
        if (coverUntilIndex == 0) {
            return;
        }

        List<ChatMessage> covered = messages.subList(0, coverUntilIndex);
        int coveredSequence = covered.getLast().sequence();
        if (coveredSequence <= session.summaryMessageSequence()) {
            return;
        }
        /*
         * SQLite 保存全量原文；summary 只是模型输入预算层的压缩视图。
         * 摘要只覆盖较早消息，最近若干条仍保留原文，避免长会话追问时丢失细节。
         */
        chatSessionRepository.updateSummary(
                conversationId,
                buildExtractiveSummary(covered),
                coveredSequence,
                System.currentTimeMillis()
        );
    }

    /**
     * 执行 聊天会话 中的 context Usage 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public ChatContextUsageResponse contextUsage(String conversationId) {
        return contextUsageService.usage(conversationId);
    }

    /**
     * 返回应用 with Context Usage 后的新对象。
     * <p>不可变数据通过复制表达变更，避免调用方误改原对象。</p>
     */
    private ChatSessionResponse withContextUsage(ChatSessionResponse response) {
        return response.withContextUsage(contextUsageService.usage(response.id()));
    }

    /**
     * 估算 estimate Chat Message 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    private int estimateChatMessage(String content) {
        ModelConfig chatConfig = modelConfigService.activeChatOrDefault();
        return tokenEstimator.estimateChatMessage(content, chatConfig);
    }

    /**
     * 构建 build Extractive Summary 对象。
     * <p>第三方 API、框架对象或复杂参数的创建细节集中在此处。</p>
     */
    private static String buildExtractiveSummary(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder(
                "以下是本会话较早内容的滚动摘要，按时间顺序保留关键事实；每条都带有当时的 Agent 模式：\n");
        for (ChatMessage message : messages) {
            String role = message.role() == ChatMessageRole.USER ? "用户" : "助手";
            String agentType = message.agentType() == null ? "UNKNOWN" : message.agentType().name();
            String line = "- [%s] %s：%s%n".formatted(agentType, role, compact(message.content()));
            if (builder.length() + line.length() > SUMMARY_MAX_CHARS) {
                builder.append("- 更早的细节仍保存在 SQLite 原始消息中，本轮仅注入摘要视图。\n");
                break;
            }
            builder.append(line);
        }
        return builder.toString().trim();
    }

    /**
     * 执行 聊天会话 中的 compact 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String compact(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "...";
    }

    /**
     * 执行 聊天会话 中的 title From Question 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private static String titleFromQuestion(String question) {
        String normalized = compact(question);
        if (normalized.isBlank()) {
            return "新对话";
        }
        return normalized.length() <= 18 ? normalized : normalized.substring(0, 18) + "...";
    }
}
