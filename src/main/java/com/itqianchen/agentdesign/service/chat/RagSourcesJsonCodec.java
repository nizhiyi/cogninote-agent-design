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
     * 注入 JSON 序列化器。
     *
     * @param objectMapper Spring 管理的 ObjectMapper
     */
    public RagSourcesJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 序列化 RAG 来源快照。
     *
     * <p>落库时去掉 content，避免聊天消息表重复保存大段 chunk 正文；详情可通过 chunkId 再查。</p>
     *
     * @param sources 本轮 RAG 来源
     * @return JSON 字符串；为空或序列化失败时返回 null
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
     * 反序列化 RAG 来源快照。
     *
     * <p>坏数据只影响来源展示，不应阻断聊天记录读取，因此失败时返回空列表。</p>
     *
     * @param json 数据库中的 JSON 字符串
     * @return 来源列表
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
