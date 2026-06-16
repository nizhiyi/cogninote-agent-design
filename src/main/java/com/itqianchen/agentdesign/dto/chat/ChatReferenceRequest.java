package com.itqianchen.agentdesign.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 前端提交的聊天引用片段。
 *
 * <p>v1 只接受助手消息中的已选文本；服务端仍保留 messageId，便于后续追踪引用来源。</p>
 */
public record ChatReferenceRequest(
        @Size(max = 80) String id,
        @NotBlank @Size(max = 80) String messageId,
        @NotBlank @Size(max = 1200) String snippet
) {
}
