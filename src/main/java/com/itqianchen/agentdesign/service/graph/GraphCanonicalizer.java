package com.itqianchen.agentdesign.service.graph;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 图谱字符串规范化工具。
 * <p>第一版只做确定性的本地规范化，不做 LLM 二次消歧，避免引入不可解释的合并。</p>
 */
@Component
public class GraphCanonicalizer {

    /**
     * 生成用于节点合并的规范名。
     *
     * @param value 原始名称
     * @return 规范化小写名称
     */
    public String canonicalName(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    /**
     * 归一化节点类型。
     *
     * @param value 模型输出类型
     * @return 大写蛇形节点类型
     */
    public String nodeType(String value) {
        return snakeUpper(value, "ENTITY");
    }

    /**
     * 归一化关系类型。
     *
     * @param value 模型输出类型
     * @return 大写蛇形关系类型
     */
    public String relationType(String value) {
        return snakeUpper(value, "RELATED_TO");
    }

    /**
     * 归一化并裁剪展示文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 展示文本
     */
    public String displayText(String value, int maxLength) {
        String normalized = normalizeText(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength)).strip();
    }

    /**
     * 判断证据 quote 是否能在原文中找到。
     *
     * @param content chunk 原文
     * @param quote 模型输出证据
     * @return 是否匹配
     */
    public boolean quoteMatches(String content, String quote) {
        String normalizedContent = normalizeEvidenceText(content);
        String normalizedQuote = normalizeEvidenceText(quote);
        return !normalizedContent.isBlank()
                && !normalizedQuote.isBlank()
                && normalizedContent.contains(normalizedQuote);
    }

    /**
     * 基于 seed 生成稳定 UUID。
     *
     * @param seed 稳定种子
     * @return UUID 字符串
     */
    public String stableId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * 将文本转换为大写蛇形标识。
     *
     * @param value 原始文本
     * @param fallback 空值兜底
     * @return 大写蛇形文本
     */
    private String snakeUpper(String value, String fallback) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        boolean previousSeparator = false;
        for (int index = 0; index < normalized.length(); index++) {
            int codePoint = normalized.codePointAt(index);
            if (Character.charCount(codePoint) == 2) {
                index++;
            }
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(Character.toUpperCase(codePoint));
                previousSeparator = false;
                continue;
            }
            if (!previousSeparator && !builder.isEmpty()) {
                builder.append('_');
                previousSeparator = true;
            }
        }
        String result = builder.toString().replaceAll("_+$", "");
        return result.isBlank() ? fallback : result;
    }

    /**
     * 归一化普通图谱文本。
     *
     * @param value 原始文本
     * @return NFKC 归一化文本
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .strip();
    }

    /**
     * 归一化证据匹配文本。
     *
     * @param value 原始文本
     * @return 去空白和标点后的文本
     */
    private static String normalizeEvidenceText(String value) {
        String normalized = normalizeText(value).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            int codePoint = normalized.codePointAt(index);
            if (Character.charCount(codePoint) == 2) {
                index++;
            }
            if (Character.isWhitespace(codePoint) || isPunctuation(codePoint)) {
                continue;
            }
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    /**
     * 判断 codePoint 是否为标点。
     *
     * @param codePoint Unicode code point
     * @return 是否标点
     */
    private static boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }
}
