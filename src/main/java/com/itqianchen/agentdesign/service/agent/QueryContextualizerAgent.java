package com.itqianchen.agentdesign.service.agent;


import com.itqianchen.agentdesign.domain.enums.chat.ChatMessageRole;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.interfaces.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.enums.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.properties.chat.ChatPromptProperties;
import com.itqianchen.agentdesign.domain.enums.chat.QueryContextualizerMode;
import com.itqianchen.agentdesign.domain.properties.chat.QueryContextualizerProperties;
import com.itqianchen.agentdesign.domain.entity.model.ModelConfig;
import com.itqianchen.agentdesign.service.chat.ChatSettingsService;
import com.itqianchen.agentdesign.service.chat.ConversationMemoryEntry;
import com.itqianchen.agentdesign.service.chat.ConversationMemorySnapshot;
import com.itqianchen.agentdesign.service.chat.ConversationMemorySnapshotService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Query Contextualizer 智能体 定义一个智能体执行路径。
 * <p>负责把用户问题、系统提示词、记忆和检索上下文组合成模型调用。</p>
 */
@Service
public class QueryContextualizerAgent {

    private static final Logger log = LoggerFactory.getLogger(QueryContextualizerAgent.class);

    private final AiRuntimeFactory aiRuntimeFactory;
    private final ChatPromptProperties promptProperties;
    private final QueryContextualizerProperties properties;
    private final ChatSettingsService chatSettingsService;
    private final ConversationMemorySnapshotService memorySnapshotService;
    private final QueryContextualizerTriggerDecider triggerDecider;
    private final ObjectMapper objectMapper;

    /**
     * 注入追问补全 Agent 依赖。
     *
     * @param aiRuntimeFactory AI 运行时工厂
     * @param promptProperties 提示词配置
     * @param properties 补全策略配置
     * @param chatSettingsService 聊天设置服务
     * @param memorySnapshotService 会话记忆快照服务
     * @param triggerDecider 本地触发判断器
     * @param objectMapper JSON 解析器
     */
    public QueryContextualizerAgent(
            AiRuntimeFactory aiRuntimeFactory,
            ChatPromptProperties promptProperties,
            QueryContextualizerProperties properties,
            ChatSettingsService chatSettingsService,
            ConversationMemorySnapshotService memorySnapshotService,
            QueryContextualizerTriggerDecider triggerDecider,
            ObjectMapper objectMapper
    ) {
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.promptProperties = promptProperties;
        this.properties = properties;
        this.chatSettingsService = chatSettingsService;
        this.memorySnapshotService = memorySnapshotService;
        this.triggerDecider = triggerDecider;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据历史上下文生成知识库检索 query。
     *
     * <p>失败时退回原问题，保证补全 Agent 不会阻断主聊天链路。</p>
     *
     * @param requestId 请求 ID
     * @param conversationId 会话 ID
     * @param question 用户原始问题
     * @param maxMessageSequenceInclusive 允许读取到的最大历史消息序号
     * @param chatConfig 当前 Chat 模型配置
     * @return 原始问题和检索 query 的组合结果
     */
    public QueryContextualization contextualize(
            String requestId,
            String conversationId,
            String question,
            int maxMessageSequenceInclusive,
            ModelConfig chatConfig
    ) {
        QueryContextualizerMode mode = chatSettingsService.queryContextualizerMode();
        if (mode == QueryContextualizerMode.OFF) {
            logSkipped(requestId, conversationId, question, mode, "query_contextualizer_off", 0);
            return QueryContextualization.original(question, "query_contextualizer_off");
        }
        try {
            ConversationMemorySnapshot snapshot = memorySnapshotService.snapshot(
                    conversationId,
                    maxMessageSequenceInclusive
            );
            QueryContextualizerTriggerDecision decision = triggerDecision(mode, question, snapshot);
            if (!decision.shouldInvoke()) {
                logSkipped(requestId, conversationId, question, mode, decision.reason(), decision.score());
                return QueryContextualization.original(question, decision.reason());
            }
            return invokeModel(requestId, conversationId, question, chatConfig, snapshot, mode, decision);
        } catch (RuntimeException ex) {
            log.warn(
                    "query_contextualizer_failed requestId={} conversationId={} originalQuestion={} reason={}",
                    requestId,
                    conversationId,
                    question,
                    ex.getMessage()
            );
            log.debug("query_contextualizer_failed_stacktrace requestId={} conversationId={}", requestId, conversationId, ex);
            return QueryContextualization.original(question, "query_contextualizer_failed");
        }
    }

    /**
     * 在 AUTO 模式下为弱检索结果执行一次补全重试。
     * <p>只有原问题没有命中知识库且存在历史时才使用，避免完整问题场景无意义地额外调用模型。</p>
     *
     * @param requestId 请求 ID
     * @param conversationId 会话 ID
     * @param question 用户原始问题
     * @param maxMessageSequenceInclusive 允许读取到的最大历史消息序号
     * @param chatConfig 当前 Chat 模型配置
     * @return 重试后的补全结果或原问题
     */
    public QueryContextualization contextualizeForWeakRetrieval(
            String requestId,
            String conversationId,
            String question,
            int maxMessageSequenceInclusive,
            ModelConfig chatConfig
    ) {
        QueryContextualizerMode mode = chatSettingsService.queryContextualizerMode();
        if (mode != QueryContextualizerMode.AUTO) {
            return QueryContextualization.original(question, "weak_retrieval_retry_not_auto");
        }
        try {
            ConversationMemorySnapshot snapshot = memorySnapshotService.snapshot(
                    conversationId,
                    maxMessageSequenceInclusive
            );
            if (!triggerDecider.hasHistory(snapshot)) {
                logSkipped(requestId, conversationId, question, mode, "auto_weak_retrieval_no_history", 0);
                return QueryContextualization.original(question, "auto_weak_retrieval_no_history");
            }
            QueryContextualizerTriggerDecision decision = new QueryContextualizerTriggerDecision(
                    true,
                    "auto_weak_retrieval_retry",
                    0
            );
            return invokeModel(requestId, conversationId, question, chatConfig, snapshot, mode, decision);
        } catch (RuntimeException ex) {
            log.warn(
                    "query_contextualizer_weak_retry_failed requestId={} conversationId={} originalQuestion={} reason={}",
                    requestId,
                    conversationId,
                    question,
                    ex.getMessage()
            );
            log.debug("query_contextualizer_weak_retry_failed_stacktrace requestId={} conversationId={}", requestId, conversationId, ex);
            return QueryContextualization.original(question, "query_contextualizer_weak_retry_failed");
        }
    }

    /**
     * 根据模式计算本轮是否调用补全模型。
     * <p>ALWAYS 保留旧行为；AUTO 交给本地打分器；OFF 在入口处已经提前返回。</p>
     *
     * @param mode 当前补全模式
     * @param question 用户原始问题
     * @param snapshot 记忆快照
     * @return 触发判断结果
     */
    private QueryContextualizerTriggerDecision triggerDecision(
            QueryContextualizerMode mode,
            String question,
            ConversationMemorySnapshot snapshot
    ) {
        if (mode == QueryContextualizerMode.ALWAYS) {
            return new QueryContextualizerTriggerDecision(true, "always_mode", 0);
        }
        return triggerDecider.decide(question, snapshot);
    }

    /**
     * 调用补全模型并解析 JSON 响应。
     * <p>所有模型调用都经过这个方法，便于统一日志、兜底和 Prompt 上下文格式。</p>
     *
     * @param requestId 请求 ID
     * @param conversationId 会话 ID
     * @param question 用户原始问题
     * @param chatConfig 当前 Chat 模型配置
     * @param snapshot 记忆快照
     * @param mode 当前补全模式
     * @param decision 本地触发判断结果
     * @return 补全结果
     */
    private QueryContextualization invokeModel(
            String requestId,
            String conversationId,
            String question,
            ModelConfig chatConfig,
            ConversationMemorySnapshot snapshot,
            QueryContextualizerMode mode,
            QueryContextualizerTriggerDecision decision
    ) {
        String history = formatHistory(snapshot);
        // 补全 Agent 只生成检索 query，不参与最终回答，也不改写用户消息原文。
        String response = aiRuntimeFactory.chatRuntime(chatConfig)
                .callText(
                        promptProperties.queryContextualizer().system(),
                        queryContextualizerUserPrompt(question, history)
                );
        QueryContextualization contextualization = applyLocalFallbackIfNeeded(
                question,
                parseResponse(question, response),
                snapshot,
                mode,
                decision
        );
        log.info(
                "query_contextualized requestId={} conversationId={} mode={} triggerReason={} triggerScore={} rewritten={} confidence={} originalQuestion={} retrievalQuery={} reason={}",
                requestId,
                conversationId,
                mode,
                decision.reason(),
                decision.score(),
                contextualization.rewritten(),
                contextualization.confidence(),
                question,
                contextualization.retrievalQuery(),
                contextualization.reason()
        );
        return contextualization;
    }

    /**
     * 对模型误判不改写的动作型追问做本地兜底。
     * <p>例如用户先问“红黑树是什么？”，再问“实现代码案例”，模型若返回不改写，
     * 检索 query 会缺少“红黑树”主题；这里仅在短动作追问且上一条用户问题有独立主题时拼接兜底。</p>
     *
     * @param question 用户原始问题
     * @param contextualization 模型补全结果
     * @param snapshot 记忆快照
     * @param mode 当前补全模式
     * @param decision 本地触发判断结果
     * @return 兜底后的补全结果
     */
    private QueryContextualization applyLocalFallbackIfNeeded(
            String question,
            QueryContextualization contextualization,
            ConversationMemorySnapshot snapshot,
            QueryContextualizerMode mode,
            QueryContextualizerTriggerDecision decision
    ) {
        if (contextualization.rewritten()
                || mode != QueryContextualizerMode.AUTO
                || !decision.shouldInvoke()
                || !triggerDecider.looksLikeActionFollowUp(question)) {
            return contextualization;
        }

        String previousQuestion = latestStandaloneUserQuestion(snapshot);
        if (previousQuestion == null || previousQuestion.isBlank()) {
            return contextualization;
        }

        String fallbackQuery = buildFallbackRetrievalQuery(previousQuestion, question);
        if (fallbackQuery.isBlank() || fallbackQuery.equals(question.strip())) {
            return contextualization;
        }
        return new QueryContextualization(
                question,
                fallbackQuery,
                true,
                "local_action_follow_up_fallback",
                Math.max(contextualization.confidence(), 0.65)
        );
    }

    /**
     * 读取最近一条有明确主题的用户问题。
     * <p>只使用用户原文，不从助手回答里抽取主题，避免把模型生成内容误当作知识库检索意图。</p>
     *
     * @param snapshot 记忆快照
     * @return 最近有独立主题的用户问题
     */
    private String latestStandaloneUserQuestion(ConversationMemorySnapshot snapshot) {
        if (snapshot == null || snapshot.recentMessages() == null) {
            return null;
        }
        List<ConversationMemoryEntry> entries = snapshot.recentMessages();
        for (int index = entries.size() - 1; index >= 0; index--) {
            ConversationMemoryEntry entry = entries.get(index);
            if (entry.role() == ChatMessageRole.USER && triggerDecider.hasStandaloneTopic(entry.content())) {
                return entry.content();
            }
        }
        return null;
    }

    /**
     * 构建本地兜底检索 query。
     * <p>前一轮主题放前面，当前动作追问放后面；长度仍遵守补全 query 的最大限制。</p>
     *
     * @param previousQuestion 最近独立主题问题
     * @param question 当前动作追问
     * @return 兜底检索 query
     */
    private String buildFallbackRetrievalQuery(String previousQuestion, String question) {
        String current = normalizeQueryText(question);
        String previous = normalizeQueryText(previousQuestion);
        int maxLength = properties.resolvedMaxRewrittenQueryLength();
        int previousLimit = maxLength - current.length() - 1;
        if (current.isBlank() || previous.isBlank() || previousLimit <= 0) {
            return "";
        }
        if (previous.length() > previousLimit) {
            previous = previous.substring(0, previousLimit).strip();
        }
        return normalizeQueryText(previous + " " + current);
    }

    /**
     * 规范化检索 query 文本。
     * <p>本地兜底只做空白折叠，不改变用户词序，保证检索意图可追溯。</p>
     *
     * @param value 原始文本
     * @return 折叠空白后的文本
     */
    private static String normalizeQueryText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    /**
     * 记录 AUTO/OFF 模式下跳过补全模型的原因。
     * <p>日志用于观察触发策略，不把这些内部细节暴露给前端聊天记录。</p>
     *
     * @param requestId 请求 ID
     * @param conversationId 会话 ID
     * @param question 用户原始问题
     * @param mode 当前补全模式
     * @param reason 跳过原因
     * @param score 本地触发分数
     */
    private void logSkipped(
            String requestId,
            String conversationId,
            String question,
            QueryContextualizerMode mode,
            String reason,
            int score
    ) {
        log.debug(
                "query_contextualizer_skipped requestId={} conversationId={} mode={} reason={} score={} question={}",
                requestId,
                conversationId,
                mode,
                reason,
                score,
                question
        );
    }

    /**
     * 组装追问补全模型的用户提示词。
     *
     * @param question 用户原始问题
     * @param history 记忆历史文本
     * @return 用户提示词
     */
    private String queryContextualizerUserPrompt(String question, String history) {
        return promptProperties.queryContextualizer().user()
                .replace("{question}", question)
                .replace("{history}", history);
    }

    /**
     * 格式化补全 Agent 可见的历史。
     *
     * <p>只取最近若干条原文并附加摘要，控制补全模型的上下文成本。</p>
     *
     * @param snapshot 记忆快照
     * @return 历史文本
     */
    private String formatHistory(ConversationMemorySnapshot snapshot) {
        List<ConversationMemoryEntry> entries = snapshot.recentMessages();
        int maxMessages = properties.resolvedMaxHistoryMessages();
        List<ConversationMemoryEntry> selected = maxMessages == 0 || entries.isEmpty()
                ? List.of()
                : entries.size() <= maxMessages
                ? entries
                : entries.subList(entries.size() - maxMessages, entries.size());
        StringBuilder builder = new StringBuilder();
        if (snapshot.summary() != null && !snapshot.summary().isBlank()) {
            builder.append("会话摘要：\n")
                    .append(snapshot.summary().strip());
        }
        if (!selected.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("最近原文消息：");
        }
        for (ConversationMemoryEntry entry : selected) {
            if (entry.content() == null || entry.content().isBlank()) {
                continue;
            }
            builder.append('\n');
            builder.append(entry.role() == ChatMessageRole.USER ? "用户" : "助手")
                    .append("：")
                    .append(entry.content().strip());
        }
        return builder.isEmpty() ? "无历史消息。" : builder.toString();
    }

    /**
     * 解析查询改写模型响应。
     *
     * <p>缺字段、空改写或过长改写都退回原问题，避免不稳定 JSON 破坏主对话链路。</p>
     *
     * @param question 用户原始问题
     * @param response 模型原始响应
     * @return 补全结果
     */
    private QueryContextualization parseResponse(String question, String response) {
        String json = extractJson(response);
        ContextualizerModelResponse parsed;
        try {
            // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
            parsed = objectMapper.readValue(json, ContextualizerModelResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid contextualizer json", ex);
        }
        if (parsed.shouldRewrite() == null || parsed.reason() == null || parsed.confidence() == null) {
            return QueryContextualization.original(question, "contextualizer_fields_missing");
        }
        if (!parsed.shouldRewrite()) {
            return QueryContextualization.original(question, nullToReason(parsed.reason(), "model_kept_original"));
        }
        String rewrittenQuery = parsed.rewrittenQuery() == null ? "" : parsed.rewrittenQuery().strip();
        if (rewrittenQuery.isBlank()) {
            return QueryContextualization.original(question, "rewritten_query_blank");
        }
        if (rewrittenQuery.length() > properties.resolvedMaxRewrittenQueryLength()) {
            return QueryContextualization.original(question, "rewritten_query_too_long");
        }
        return new QueryContextualization(
                question,
                rewrittenQuery,
                true,
                nullToReason(parsed.reason(), "model_rewrote_query"),
                normalizeConfidence(parsed.confidence())
        );
    }

    /**
     * 从模型响应中截取 JSON 对象。
     *
     * @param response 模型原始响应
     * @return JSON 对象字符串
     */
    private static String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("contextualizer response is blank");
        }
        String stripped = response.strip();
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("contextualizer response does not contain json object");
        }
        return stripped.substring(start, end + 1);
    }

    /**
     * 归一化原因字段。
     *
     * @param value 模型返回原因
     * @param fallback 兜底原因
     * @return 非空原因
     */
    private static String nullToReason(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    /**
     * 归一化模型置信度。
     *
     * @param confidence 模型返回置信度
     * @return 0 到 1 之间的置信度
     */
    private static double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return 0.0;
        }
        return Math.clamp(confidence, 0.0, 1.0);
    }

    /**
     * Contextualizer Model 响应 定义返回给前端的 智能体编排 响应结构。
     * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
     */
    private record ContextualizerModelResponse(
            Boolean shouldRewrite,
            String rewrittenQuery,
            String reason,
            Double confidence
    ) {
    }
}
