package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.dto.chat.ChatReferenceRequest;
import com.itqianchen.agentdesign.dto.chat.ChatReferenceResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 规范化用户选择的引用片段。
 *
 * <p>前端会先限制数量和长度；服务端再次裁剪，避免旧客户端或手写请求把过长文本塞进上下文。</p>
 */
@Component
public class ChatReferenceSanitizer {

    public static final int MAX_REFERENCES = 5;
    public static final int MAX_SNIPPET_CHARS = 1200;
    public static final int MAX_TOTAL_SNIPPET_CHARS = 4000;

    /**
     * 清洗前端请求中的引用片段。
     *
     * @param references 原始引用请求
     * @return 可落库和注入模型上下文的引用片段
     */
    public List<ChatReferenceResponse> sanitizeRequests(List<ChatReferenceRequest> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        List<ChatReferenceResponse> sanitized = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int totalChars = 0;
        for (ChatReferenceRequest reference : references) {
            if (reference == null || sanitized.size() >= MAX_REFERENCES) {
                continue;
            }
            String messageId = normalizeWhitespace(reference.messageId());
            String snippet = truncate(normalizeWhitespace(reference.snippet()), MAX_SNIPPET_CHARS);
            if (messageId.isBlank() || snippet.isBlank()) {
                continue;
            }
            int remaining = MAX_TOTAL_SNIPPET_CHARS - totalChars;
            if (remaining <= 0) {
                break;
            }
            snippet = truncate(snippet, remaining);
            String dedupeKey = messageId + "\n" + snippet;
            if (!seen.add(dedupeKey)) {
                continue;
            }
            sanitized.add(new ChatReferenceResponse(
                    normalizeId(reference.id()),
                    messageId,
                    snippet
            ));
            totalChars += snippet.length();
        }
        return List.copyOf(sanitized);
    }

    /**
     * 清洗从数据库 JSON 读取出的引用片段。
     *
     * @param references 数据库存储的引用
     * @return 安全可展示的引用片段
     */
    public List<ChatReferenceResponse> sanitizeResponses(List<ChatReferenceResponse> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        List<ChatReferenceRequest> requests = references.stream()
                .filter(reference -> reference != null)
                .map(reference -> new ChatReferenceRequest(
                        reference.id(),
                        reference.messageId(),
                        reference.snippet()
                ))
                .toList();
        return sanitizeRequests(requests);
    }

    private static String normalizeId(String id) {
        String normalized = normalizeWhitespace(id);
        if (normalized.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return truncate(normalized, 80);
    }

    private static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || maxChars <= 0) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
