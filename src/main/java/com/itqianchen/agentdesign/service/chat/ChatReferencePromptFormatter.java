package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.dto.chat.ChatReferenceResponse;
import java.util.List;

/**
 * 把聊天引用片段格式化为模型可理解的用户输入。
 *
 * <p>数据库里的用户 content 始终保持原始问题；只有进入模型上下文和 token 估算时才拼接引用块。</p>
 */
public final class ChatReferencePromptFormatter {

    private ChatReferencePromptFormatter() {
    }

    /**
     * 构造模型实际看到的用户消息。
     *
     * @param question 用户原始问题
     * @param references 已清洗的引用片段
     * @return 无引用时返回原始问题，有引用时返回引用块加问题
     */
    public static String formatUserContent(String question, List<ChatReferenceResponse> references) {
        String normalizedQuestion = question == null ? "" : question.trim();
        if (references == null || references.isEmpty()) {
            return normalizedQuestion;
        }
        StringBuilder builder = new StringBuilder("""
                用户本轮引用了以下助手回复片段，请优先结合这些片段回答；不要把引用片段当作用户新说的话。

                """);
        for (int index = 0; index < references.size(); index += 1) {
            ChatReferenceResponse reference = references.get(index);
            if (reference == null || reference.snippet() == null || reference.snippet().isBlank()) {
                continue;
            }
            builder.append("[引用 ").append(index + 1).append("]\n")
                    .append(reference.snippet().trim())
                    .append("\n\n");
        }
        builder.append("用户问题：\n").append(normalizedQuestion);
        return builder.toString().trim();
    }
}
