package com.itqianchen.agentdesign.domain.dto.chat;

/**
 * 返回给前端展示的聊天引用片段。
 *
 * <p>content 字段仍只保存用户原始问题，引用片段通过该结构单独恢复。</p>
 */
public record ChatReferenceResponse(
        String id,
        String messageId,
        String snippet
) {
}
