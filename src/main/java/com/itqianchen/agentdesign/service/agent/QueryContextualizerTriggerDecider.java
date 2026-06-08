package com.itqianchen.agentdesign.service.agent;

import com.itqianchen.agentdesign.service.chat.ConversationMemorySnapshot;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * AUTO 模式下的本地追问补全触发判断器。
 * <p>它不做精确短语命中，而是组合省略、指代、延续动作和完整问题反向信号进行轻量打分。</p>
 */
@Component
public class QueryContextualizerTriggerDecider {

    private static final int INVOKE_THRESHOLD = 3;
    private static final int VERY_SHORT_QUESTION_LENGTH = 12;
    private static final int SHORT_QUESTION_LENGTH = 24;

    private static final Pattern CONTEXT_REFERENCE_SIGNAL = Pattern.compile(
            "(它|他们|她们|这个|那个|这些|那些|这种|那种|这段|那段|这份|那份|这条|那条|这里|"
                    + "上面|前面|上文|前文|之前|刚才|刚刚|刚提到|刚说|上面提到|前面提到|你说的|"
                    + "上一(个|条|轮|次)|前一(个|条|轮|次)|前者|后者|该|其|其中|这个呢|那个呢|"
                    + "那(应该|要|可以|能|该|怎么办|怎么|如何|有没有|是否))"
    );
    private static final Pattern CONTINUATION_SIGNAL = Pattern.compile(
            "(继续|展开|详细|详解|补充|拓展|延伸|举例|例子|示例|代码|案例|实现|写一下|写个|生成|给我|"
                    + "demo|Demo|总结|小结|归纳|再来|还有|换一个|换种|对比|比较|区别|优缺点|适用场景|"
                    + "怎么实现|怎么用|怎么配置|怎么部署|怎么排查|怎么修复|怎么优化|怎么办|咋办|咋整|"
                    + "怎么处理|如何处理|能不能|可以吗|有没有|是否支持)"
    );
    private static final Pattern ACTION_FOLLOW_UP_SIGNAL = Pattern.compile(
            "(继续|展开|详细|详解|说明|解释|介绍|补充|拓展|举例|例子|示例|代码|案例|实现|写一下|写个|"
                    + "生成|demo|Demo|总结|小结|归纳|再来|对比|比较|区别|优缺点|适用场景|怎么实现|怎么用|"
                    + "怎么配置|怎么部署|怎么排查|怎么修复|怎么优化|怎么办|咋办|咋整|怎么处理|如何处理)"
    );
    private static final Pattern ELLIPSIS_COMPLETION_SIGNAL = Pattern.compile(
            "(哪个|哪些|哪种|哪一个|更适合|有没有|是否|支持吗|能吗|可以吗|多少钱|价格|费用|证书|"
                    + "退款|试听|分期|有什么影响|怎么治疗|吃什么药|应该怎么|应该吃|怎么做|怎么弄)"
    );
    private static final Pattern ENGLISH_REFINEMENT_SIGNAL = Pattern.compile(
            "(^|\\b)((in|for|about|within|on)\\s+[A-Za-z][A-Za-z0-9_+#.\\-]{2,}|"
                    + "what about|how about|and then|also|more about|continue|details?|example|"
                    + "code sample|implementation|compare|difference|pros and cons|summari[sz]e)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STANDALONE_TECHNICAL_TOKEN = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_+#.\\-]{2,}"
    );
    private static final Pattern STANDALONE_CHINESE_TOPIC = Pattern.compile(
            "[\\p{IsHan}]{2,}(是什么|怎么|如何|为什么|区别|原理|流程|配置|实现|问题|方案)"
    );

    /**
     * 判断当前问题是否值得调用补全 Agent。
     * <p>没有历史时补全无法补充上下文，完整问题也应直接检索以减少额外延迟。</p>
     */
    public QueryContextualizerTriggerDecision decide(String question, ConversationMemorySnapshot snapshot) {
        if (!hasHistory(snapshot)) {
            return new QueryContextualizerTriggerDecision(false, "auto_no_history", 0);
        }

        String normalized = normalize(question);
        if (normalized.isBlank()) {
            return new QueryContextualizerTriggerDecision(false, "auto_blank_question", 0);
        }

        int score = 0;
        int length = normalized.length();
        if (length <= VERY_SHORT_QUESTION_LENGTH) {
            score += 2;
        } else if (length <= SHORT_QUESTION_LENGTH) {
            score += 1;
        }
        boolean standaloneTopic = containsStandaloneTopic(normalized);
        boolean hasContextReference = CONTEXT_REFERENCE_SIGNAL.matcher(normalized).find();
        boolean hasEnglishRefinement = ENGLISH_REFINEMENT_SIGNAL.matcher(normalized).find();
        if (hasContextReference) {
            score += 4;
        }
        if (CONTINUATION_SIGNAL.matcher(normalized).find()) {
            score += 2;
        }
        if (ELLIPSIS_COMPLETION_SIGNAL.matcher(normalized).find()) {
            score += standaloneTopic ? 1 : 2;
        }
        if (hasEnglishRefinement) {
            score += 2;
        }
        if (looksLikeFragment(normalized)) {
            score += 1;
        }
        if (looksLikeStandaloneQuestion(normalized) && !hasContextReference && !hasEnglishRefinement) {
            score -= 3;
        }

        boolean shouldInvoke = score >= INVOKE_THRESHOLD;
        String reason = shouldInvoke ? "auto_follow_up_score" : "auto_standalone_question";
        return new QueryContextualizerTriggerDecision(shouldInvoke, reason, score);
    }

    /**
     * 判断快照中是否存在可用于补全的历史上下文。
     * <p>摘要和最近消息都算历史来源；压缩会话不能只看最近消息。</p>
     */
    public boolean hasHistory(ConversationMemorySnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.summary() != null && !snapshot.summary().isBlank()) {
            return true;
        }
        return snapshot.recentMessages() != null && !snapshot.recentMessages().isEmpty();
    }

    /**
     * 判断当前问题是否像“实现代码案例”这类动作型追问。
     * <p>该判断只给模型误判时的本地兜底使用，要求短句、有动作信号、且自身没有明确独立主题。</p>
     */
    public boolean looksLikeActionFollowUp(String question) {
        String normalized = normalize(question);
        return !normalized.isBlank()
                && normalized.length() <= SHORT_QUESTION_LENGTH
                && (ACTION_FOLLOW_UP_SIGNAL.matcher(normalized).find()
                || ENGLISH_REFINEMENT_SIGNAL.matcher(normalized).find())
                && !containsStandaloneTopic(normalized);
    }

    /**
     * 判断文本里是否包含可独立检索的主题。
     * <p>暴露给补全 Agent 做本地兜底，避免把上一轮同样省略的短句误当作主题来源。</p>
     */
    public boolean hasStandaloneTopic(String text) {
        return containsStandaloneTopic(normalize(text));
    }

    /**
     * 判断问题是否更像省略片段。
     * <p>短句、动宾式要求和未点名主体的问题，通常需要历史主题才能检索准确。</p>
     */
    private static boolean looksLikeFragment(String question) {
        if ((question.endsWith("呢") || question.endsWith("？") && question.length() <= VERY_SHORT_QUESTION_LENGTH)
                && !containsStandaloneTopic(question)) {
            return true;
        }
        return !containsStandaloneTopic(question) && question.length() <= SHORT_QUESTION_LENGTH;
    }

    /**
     * 判断问题是否更像完整独立问题。
     * <p>包含明确技术词或中文主题，并带有“是什么/如何/为什么”等问法时，通常不需要补全。</p>
     */
    private static boolean looksLikeStandaloneQuestion(String question) {
        return containsStandaloneTopic(question);
    }

    /**
     * 判断文本中是否有可独立检索的主题。
     * <p>英文技术词和中文主题都纳入，但只作为反向信号，不直接决定最终结果。</p>
     */
    private static boolean containsStandaloneTopic(String question) {
        return STANDALONE_TECHNICAL_TOKEN.matcher(question).find()
                || STANDALONE_CHINESE_TOPIC.matcher(question).find();
    }

    /**
     * 规范化用户输入。
     * <p>去掉多余空白，降低换行和复制文本对打分的影响。</p>
     */
    private static String normalize(String question) {
        return question == null ? "" : question.replaceAll("\\s+", " ").trim();
    }
}
