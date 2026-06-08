package com.itqianchen.agentdesign.service.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itqianchen.agentdesign.dto.chat.RagSourceResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rag Sources Json Codec 负责 聊天会话 数据的序列化和反序列化。
 * <p>这里隔离存储格式，避免 JSON 细节扩散到业务层。</p>
 */
@Component
public class RagSourcesJsonCodec {

    private static final Logger log = LoggerFactory.getLogger(RagSourcesJsonCodec.class);
    private static final TypeReference<List<RagSourceResponse>> SOURCES_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 注入 RagSourcesJsonCodec 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public RagSourcesJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 聊天会话 中的 encode 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String encode(List<RagSourceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
            return objectMapper.writeValueAsString(sources.stream()
                    .map(source -> source.withContent(null))
                    .toList());
        } catch (JsonProcessingException ex) {
            log.warn("rag_sources_encode_failed", ex);
            return null;
        }
    }

    /**
     * 执行 聊天会话 中的 decode 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public List<RagSourceResponse> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            // JSON 编解码在边界层完成，避免序列化细节泄漏到业务对象。
            return objectMapper.readValue(json, SOURCES_TYPE);
        } catch (JsonProcessingException ex) {
            log.warn("rag_sources_decode_failed", ex);
            return List.of();
        }
    }
}
