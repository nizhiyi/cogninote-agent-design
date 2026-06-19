package com.itqianchen.agentdesign.service.graph;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 图谱字符串规范化工具。
 * <p>第一版只做确定性的本地规范化，不做 LLM 二次消歧，避免引入不可解释的合并。</p>
 */
@Component
public class GraphCanonicalizer {

    private static final String RELATED = "RELATED";
    private static final String DEFAULT_RELATION_DISPLAY_LABEL = "相关";
    private static final int MAX_RELATION_DISPLAY_LABEL_LENGTH = 8;
    private static final Set<String> COARSE_RELATION_TYPES = Set.of(
            RELATED,
            "STRUCTURAL",
            "FUNCTIONAL",
            "CAUSAL",
            "SEQUENCE",
            "OWNERSHIP",
            "COMPARISON",
            "CONSTRAINT"
    );
    // 仅用于读取旧缓存和旧事实表：旧英文细关系不能再作为 UI 展示文案，只能归入 8 类粗分类。
    private static final Map<String, String> LEGACY_RELATION_TYPE_ALIASES = Map.ofEntries(
            Map.entry("RELATED_TO", RELATED),
            Map.entry("RELATES_TO", RELATED),
            Map.entry("HAS", "STRUCTURAL"),
            Map.entry("HAS_PART", "STRUCTURAL"),
            Map.entry("PART_OF", "STRUCTURAL"),
            Map.entry("CONTAINS", "STRUCTURAL"),
            Map.entry("INCLUDES", "STRUCTURAL"),
            Map.entry("TYPE_OF", "STRUCTURAL"),
            Map.entry("INSTANCE_OF", "STRUCTURAL"),
            Map.entry("IS_A", "STRUCTURAL"),
            Map.entry("USES", "FUNCTIONAL"),
            Map.entry("USED_FOR", "FUNCTIONAL"),
            Map.entry("BUILT_WITH", "FUNCTIONAL"),
            Map.entry("CALLS", "FUNCTIONAL"),
            Map.entry("READS", "FUNCTIONAL"),
            Map.entry("WRITES", "FUNCTIONAL"),
            Map.entry("STORES", "FUNCTIONAL"),
            Map.entry("QUERIES", "FUNCTIONAL"),
            Map.entry("CREATES", "FUNCTIONAL"),
            Map.entry("PRODUCES", "FUNCTIONAL"),
            Map.entry("CONSUMES", "FUNCTIONAL"),
            Map.entry("CONFIGURES", "FUNCTIONAL"),
            Map.entry("IMPLEMENTS", "FUNCTIONAL"),
            Map.entry("CAUSES", "CAUSAL"),
            Map.entry("CAUSED_BY", "CAUSAL"),
            Map.entry("RESULTS_IN", "CAUSAL"),
            Map.entry("LEADS_TO", "CAUSAL"),
            Map.entry("TRIGGERS", "CAUSAL"),
            Map.entry("NOTIFIES", "CAUSAL"),
            Map.entry("NOTIFIES_ABOUT", "CAUSAL"),
            Map.entry("PRECEDES", "SEQUENCE"),
            Map.entry("FOLLOWS", "SEQUENCE"),
            Map.entry("OWNS", "OWNERSHIP"),
            Map.entry("OWNED_BY", "OWNERSHIP"),
            Map.entry("MANAGES", "OWNERSHIP"),
            Map.entry("MANAGED_BY", "OWNERSHIP"),
            Map.entry("CONTROLLED_BY", "OWNERSHIP"),
            Map.entry("COMPARED_WITH", "COMPARISON"),
            Map.entry("CONTRASTS_WITH", "COMPARISON"),
            Map.entry("ALTERNATIVE_TO", "COMPARISON"),
            Map.entry("SIMILAR_TO", "COMPARISON"),
            Map.entry("EVALUATES", "COMPARISON"),
            Map.entry("REQUIRES", "CONSTRAINT"),
            Map.entry("DEPENDS_ON", "CONSTRAINT"),
            Map.entry("GOVERNED_BY", "CONSTRAINT"),
            Map.entry("PROTECTS_AGAINST", "CONSTRAINT"),
            Map.entry("PREVENTS", "CONSTRAINT"),
            Map.entry("AVOIDS", "CONSTRAINT")
    );

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
     * 归一化关系粗分类。
     *
     * @param value 模型输出类型
     * @return 允许的关系粗分类
     */
    public String relationType(String value) {
        String normalized = snakeUpper(value, RELATED);
        if (COARSE_RELATION_TYPES.contains(normalized)) {
            return normalized;
        }
        return LEGACY_RELATION_TYPE_ALIASES.getOrDefault(normalized, RELATED);
    }

    /**
     * 归一化关系展示谓词。
     *
     * @param value 模型输出的中文短谓词
     * @return 可直接展示的中文短标签
     */
    public String relationDisplayLabel(String value) {
        String normalized = normalizeText(value).replaceAll("\\s+", "");
        // displayLabel 会直接出现在边标签上，含英文或过长时宁可兜底，避免把模型噪音暴露给用户。
        if (normalized.isBlank()
                || normalized.length() > MAX_RELATION_DISPLAY_LABEL_LENGTH
                || !containsChinese(normalized)
                || containsAsciiLetter(normalized)
                || isOnlySymbols(normalized)) {
            return DEFAULT_RELATION_DISPLAY_LABEL;
        }
        return normalized;
    }

    /**
     * 归一化关系完整描述。
     *
     * <p>关系描述最终会直接展示给用户；模型输出纯英文时，使用已清洗的中文谓词构造保守兜底句。</p>
     *
     * @param source 关系起点展示名
     * @param target 关系终点展示名
     * @param displayLabel 中文关系谓词
     * @param value 模型输出的关系描述
     * @param maxLength 最大长度
     * @return 中文关系描述
     */
    public String relationDescription(
            String source,
            String target,
            String displayLabel,
            String value,
            int maxLength
    ) {
        String normalized = displayText(value, maxLength);
        if (containsChinese(normalized)) {
            return normalized;
        }
        String safeSource = displayText(source, 120);
        String safeTarget = displayText(target, 120);
        String safeLabel = relationDisplayLabel(displayLabel);
        // 这里不是翻译英文描述，只在模型没有给出中文句子时构造一个保守、可读的事实陈述。
        String fallback;
        if (!safeSource.isBlank() && !safeTarget.isBlank()) {
            fallback = safeSource + " " + safeLabel + " " + safeTarget + "。";
        } else if (!safeSource.isBlank()) {
            fallback = safeSource + " " + safeLabel + "。";
        } else if (!safeTarget.isBlank()) {
            fallback = safeLabel + " " + safeTarget + "。";
        } else {
            fallback = safeLabel + "。";
        }
        return displayText(fallback, maxLength);
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

    private static boolean containsChinese(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private static boolean containsAsciiLetter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z'));
    }

    private static boolean isOnlySymbols(String value) {
        return value.codePoints().noneMatch(Character::isLetterOrDigit);
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
