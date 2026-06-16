package com.itqianchen.agentdesign.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.dto.chat.ChatReferenceResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 聊天引用片段 JSON 编解码器。
 *
 * <p>引用属于用户消息的展示和模型上下文元数据，集中编解码可避免存储格式扩散到业务层。</p>
 */
@Component
public class ChatReferencesJsonCodec {

    private static final Logger log = LoggerFactory.getLogger(ChatReferencesJsonCodec.class);
    private static final TypeReference<List<ChatReferenceResponse>> REFERENCES_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final ChatReferenceSanitizer chatReferenceSanitizer;

    /**
     * 注入 JSON 序列化器和引用清洗器。
     *
     * @param objectMapper Spring 管理的 ObjectMapper
     * @param chatReferenceSanitizer 引用边界清洗器
     */
    public ChatReferencesJsonCodec(ObjectMapper objectMapper, ChatReferenceSanitizer chatReferenceSanitizer) {
        this.objectMapper = objectMapper;
        this.chatReferenceSanitizer = chatReferenceSanitizer;
    }

    /**
     * 序列化引用片段。
     *
     * @param references 引用列表
     * @return JSON 字符串；为空或失败时返回 null
     */
    public String encode(List<ChatReferenceResponse> references) {
        List<ChatReferenceResponse> sanitized = chatReferenceSanitizer.sanitizeResponses(references);
        if (sanitized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException ex) {
            log.warn("chat_references_encode_failed", ex);
            return null;
        }
    }

    /**
     * 反序列化引用片段。
     *
     * <p>坏数据只影响引用展示和上下文增强，不应阻断聊天记录读取。</p>
     *
     * @param json 数据库中的 JSON 字符串
     * @return 引用片段列表
     */
    public List<ChatReferenceResponse> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return chatReferenceSanitizer.sanitizeResponses(objectMapper.readValue(json, REFERENCES_TYPE));
        } catch (JsonProcessingException ex) {
            log.warn("chat_references_decode_failed", ex);
            return List.of();
        }
    }
}
