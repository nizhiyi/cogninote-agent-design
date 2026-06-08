package com.itqianchen.agentdesign.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.agent.AgentType;
import com.itqianchen.agentdesign.domain.chat.ChatMessageRole;
import com.itqianchen.agentdesign.domain.search.SearchMode;
import com.itqianchen.agentdesign.service.agent.QueryContextualizerTriggerDecider;
import com.itqianchen.agentdesign.service.chat.ConversationMemoryEntry;
import com.itqianchen.agentdesign.service.chat.ConversationMemorySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 追问补全触发打分器测试。
 * <p>用真实多轮 RAG 场景固定词类行为，避免后续调整词表时重新漏掉省略、指代和领域切换追问。</p>
 */
class QueryContextualizerTriggerDeciderTests {

    private final QueryContextualizerTriggerDecider decider = new QueryContextualizerTriggerDecider();

    /**
     * 验证中文指代词能触发补全。
     * <p>“它/这个/上面说的”这类词对人明确，对检索器却缺少主题。</p>
     */
    @Test
    void invokesForChineseCoreferenceFollowUps() {
        ConversationMemorySnapshot snapshot = snapshot("Python入门课多少钱？");

        assertShouldInvoke("那它有没有证书", snapshot);
        assertShouldInvoke("这个怎么退款", snapshot);
        assertShouldInvoke("上面说的那个能试听吗", snapshot);
    }

    /**
     * 验证中文省略式比较和处理类追问能触发补全。
     * <p>这些问题自身包含动作或属性，但省略了上一轮的比较对象或业务主体。</p>
     */
    @Test
    void invokesForChineseEllipsisAndActionFollowUps() {
        assertShouldInvoke(
                "哪个更适合微服务",
                snapshot("Spring Boot和Spring Cloud有什么区别？")
        );
        assertShouldInvoke(
                "那应该怎么治疗呢？",
                snapshot("怀孕期间有抑郁倾向会有什么影响？")
        );
        assertShouldInvoke(
                "实现代码案例",
                snapshot("红黑树是什么？")
        );
    }

    /**
     * 验证英文领域切换追问能触发补全。
     * <p>资料中常见的 “in travel” 类型没有代词，但本质是在上一轮主题上替换约束条件。</p>
     */
    @Test
    void invokesForEnglishDomainRefinementFollowUps() {
        ConversationMemorySnapshot snapshot = snapshot("What work have we done in retail?");

        assertShouldInvoke("in travel", snapshot);
        assertShouldInvoke("what about finance", snapshot);
        assertShouldInvoke("code sample", snapshot);
    }

    /**
     * 验证短但自包含的问题不会仅因有历史而触发补全。
     * <p>AUTO 模式要避免把完整新问题都送去补全模型，否则会增加不必要延迟。</p>
     */
    @Test
    void skipsShortStandaloneQuestionsWithExplicitTopics() {
        ConversationMemorySnapshot snapshot = snapshot("HashMap 是怎么扩容的？");

        assertShouldSkip("红黑树是什么？", snapshot);
        assertShouldSkip("Redis 有哪些持久化方案？", snapshot);
    }

    /**
     * 断言问题应该触发补全。
     * <p>失败时输出分数和原因，便于快速判断是词类还是阈值问题。</p>
     */
    private void assertShouldInvoke(String question, ConversationMemorySnapshot snapshot) {
        var decision = decider.decide(question, snapshot);
        assertThat(decision.shouldInvoke())
                .as("question=%s reason=%s score=%s", question, decision.reason(), decision.score())
                .isTrue();
    }

    /**
     * 断言问题应该跳过补全。
     * <p>完整问题跳过是 AUTO 模式降低额外模型调用成本的关键保护。</p>
     */
    private void assertShouldSkip(String question, ConversationMemorySnapshot snapshot) {
        var decision = decider.decide(question, snapshot);
        assertThat(decision.shouldInvoke())
                .as("question=%s reason=%s score=%s", question, decision.reason(), decision.score())
                .isFalse();
    }

    /**
     * 构造带有上一轮用户问题的记忆快照。
     * <p>打分器只需要判断是否存在历史和最近用户内容，其他 token 字段在这里填入安全默认值。</p>
     */
    private static ConversationMemorySnapshot snapshot(String previousQuestion) {
        return new ConversationMemorySnapshot(
                null,
                List.of(new ConversationMemoryEntry(
                        AgentType.KNOWLEDGE_BASE,
                        ChatMessageRole.USER,
                        previousQuestion,
                        SearchMode.HYBRID
                )),
                1,
                0,
                0,
                1,
                128000,
                102400,
                "test"
        );
    }
}
