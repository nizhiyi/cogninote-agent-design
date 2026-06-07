package com.itqianchen.agentdesign.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.domain.ai.AiRuntimeFactory;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.chat.ChatPromptProperties;
import com.itqianchen.agentdesign.domain.chat.QueryContextualizerProperties;
import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.service.chat.ConversationMemoryEntry;
import com.itqianchen.agentdesign.service.chat.ConversationMemorySnapshot;
import com.itqianchen.agentdesign.service.chat.ConversationMemorySnapshotService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueryContextualizerAgent {

    private static final Logger log = LoggerFactory.getLogger(QueryContextualizerAgent.class);

    private final AiRuntimeFactory aiRuntimeFactory;
    private final ChatPromptProperties promptProperties;
    private final QueryContextualizerProperties properties;
    private final ConversationMemorySnapshotService memorySnapshotService;
    private final ObjectMapper objectMapper;

    public QueryContextualizerAgent(
            AiRuntimeFactory aiRuntimeFactory,
            ChatPromptProperties promptProperties,
            QueryContextualizerProperties properties,
            ConversationMemorySnapshotService memorySnapshotService,
            ObjectMapper objectMapper
    ) {
        this.aiRuntimeFactory = aiRuntimeFactory;
        this.promptProperties = promptProperties;
        this.properties = properties;
        this.memorySnapshotService = memorySnapshotService;
        this.objectMapper = objectMapper;
    }

    public QueryContextualization contextualize(
            String requestId,
            String conversationId,
            String question,
            int maxMessageSequenceInclusive,
            ModelConfig chatConfig
    ) {
        if (!properties.resolvedEnabled()) {
            return QueryContextualization.original(question, "query_contextualizer_disabled");
        }
        try {
            ConversationMemorySnapshot snapshot = memorySnapshotService.snapshot(
                    conversationId,
                    maxMessageSequenceInclusive
            );
            String history = formatHistory(snapshot.recentMessages());
            String response = aiRuntimeFactory.chatRuntime(chatConfig)
                    .callText(
                            promptProperties.queryContextualizer().system(),
                            queryContextualizerUserPrompt(question, history)
                    );
            QueryContextualization contextualization = parseResponse(question, response);
            log.info(
                    "query_contextualized requestId={} conversationId={} rewritten={} confidence={} originalQuestion={} retrievalQuery={} reason={}",
                    requestId,
                    conversationId,
                    contextualization.rewritten(),
                    contextualization.confidence(),
                    question,
                    contextualization.retrievalQuery(),
                    contextualization.reason()
            );
            return contextualization;
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

    private String queryContextualizerUserPrompt(String question, String history) {
        return promptProperties.queryContextualizer().user()
                .replace("{question}", question)
                .replace("{history}", history);
    }

    private String formatHistory(List<ConversationMemoryEntry> entries) {
        int maxMessages = properties.resolvedMaxHistoryMessages();
        if (maxMessages == 0 || entries.isEmpty()) {
            return "无历史消息。";
        }
        List<ConversationMemoryEntry> selected = entries.size() <= maxMessages
                ? entries
                : entries.subList(entries.size() - maxMessages, entries.size());
        StringBuilder builder = new StringBuilder();
        for (ConversationMemoryEntry entry : selected) {
            if (entry.content() == null || entry.content().isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(entry.role() == ChatMessageRole.USER ? "用户" : "助手")
                    .append("：")
                    .append(entry.content().strip());
        }
        return builder.isEmpty() ? "无历史消息。" : builder.toString();
    }

    private QueryContextualization parseResponse(String question, String response) {
        String json = extractJson(response);
        ContextualizerModelResponse parsed;
        try {
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

    private static String nullToReason(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static double normalizeConfidence(Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return 0.0;
        }
        return Math.clamp(confidence, 0.0, 1.0);
    }

    private record ContextualizerModelResponse(
            Boolean shouldRewrite,
            String rewrittenQuery,
            String reason,
            Double confidence
    ) {
    }
}
