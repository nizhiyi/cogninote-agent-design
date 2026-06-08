package com.itqianchen.agentdesign.service.chat;

import com.itqianchen.agentdesign.domain.model.ModelConfig;
import com.itqianchen.agentdesign.domain.model.ModelProvider;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * TokenEstimator 负责估算 token 用量。
 * <p>估算结果用于上下文裁剪、压缩策略和前端占用展示。</p>
 */
@Component
public class TokenEstimator {

    private static final int CHAT_MESSAGE_OVERHEAD_TOKENS = 4;

    private final EncodingRegistry encodingRegistry = Encodings.newLazyEncodingRegistry();

    /**
     * 估算 estimate 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimate(String text) {
        return estimate(text, null);
    }

    /**
     * 估算 estimate 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimate(String text, ModelConfig config) {
        return estimateWithMethod(text, config).tokens();
    }

    /**
     * 估算 estimate Chat Message 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public int estimateChatMessage(String text, ModelConfig config) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return estimate(text, config) + CHAT_MESSAGE_OVERHEAD_TOKENS;
    }

    /**
     * 估算 estimate With Method 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    public TokenEstimate estimateWithMethod(String text, ModelConfig config) {
        if (text == null || text.isBlank()) {
            return new TokenEstimate(0, "empty");
        }
        try {
            EncodingChoice choice = chooseEncoding(config);
            return new TokenEstimate(Math.max(1, choice.encoding().countTokens(text)), choice.method());
        } catch (RuntimeException ex) {
            return new TokenEstimate(heuristicEstimate(text), "heuristic:mixed-script");
        }
    }

    /**
     * 选择 choose Encoding 对应的可用策略。
     * <p>优先走精确匹配，失败时回退到兼容方案。</p>
     */
    private EncodingChoice chooseEncoding(ModelConfig config) {
        String modelName = config == null ? "" : nullToBlank(config.modelName()).trim();
        if (!modelName.isBlank()) {
            try {
                // JTokkit tokenizer 选择决定上下文预算估算口径，失败时必须走保守兜底。
                return encodingRegistry.getEncodingForModel(modelName)
                        .map(encoding -> new EncodingChoice(encoding, "jtokkit:" + encoding.getName()))
                        .orElseGet(() -> fallbackEncoding(config, modelName));
            } catch (RuntimeException ignored) {
                return fallbackEncoding(config, modelName);
            }
        }
        return fallbackEncoding(config, modelName);
    }

    /**
     * 选择 fallback Encoding 对应的可用策略。
     * <p>优先走精确匹配，失败时回退到兼容方案。</p>
     */
    private EncodingChoice fallbackEncoding(ModelConfig config, String modelName) {
        String normalized = modelName.toLowerCase(Locale.ROOT);
        boolean preferO200K = config != null && config.provider() == ModelProvider.DASHSCOPE
                || normalized.contains("qwen")
                || normalized.contains("gpt-4o")
                || normalized.contains("gpt-5");
        EncodingType encodingType = preferO200K ? EncodingType.O200K_BASE : EncodingType.CL100K_BASE;
        // JTokkit tokenizer 选择决定上下文预算估算口径，失败时必须走保守兜底。
        Encoding encoding = encodingRegistry.getEncoding(encodingType);
        return new EncodingChoice(encoding, "jtokkit:" + encoding.getName());
    }

    /**
     * 在 tokenizer 不可用时执行保守 token 估算。
     * <p>混合中英文文本按更谨慎的规则估算，避免上下文预算被低估。</p>
     */
    private static int heuristicEstimate(String text) {
        double tokens = 0.0;
        int asciiRunLength = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint <= 0x7F) {
                asciiRunLength++;
                continue;
            }
            if (asciiRunLength > 0) {
                tokens += Math.ceil(asciiRunLength / 4.0);
                asciiRunLength = 0;
            }
            tokens += Character.isWhitespace(codePoint) ? 0.25 : 1.0;
        }
        if (asciiRunLength > 0) {
            tokens += Math.ceil(asciiRunLength / 4.0);
        }
        return Math.max(1, (int) Math.ceil(tokens));
    }

    /**
     * 将可空字符串转换为空串。
     * <p>调用方可直接进行 trim、contains 等字符串操作。</p>
     */
    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    /**
     * Token Estimate 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    public record TokenEstimate(int tokens, String method) {
    }

    /**
     * Encoding Choice 是 聊天会话 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record EncodingChoice(Encoding encoding, String method) {
    }
}
