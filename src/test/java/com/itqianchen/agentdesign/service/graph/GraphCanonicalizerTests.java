package com.itqianchen.agentdesign.service.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GraphCanonicalizerTests {

    private final GraphCanonicalizer canonicalizer = new GraphCanonicalizer();

    @Test
    void relationTypeKeepsOnlyAllowedCoarseCategories() {
        assertThat(canonicalizer.relationType("FUNCTIONAL")).isEqualTo("FUNCTIONAL");
        assertThat(canonicalizer.relationType("USES")).isEqualTo("FUNCTIONAL");
        assertThat(canonicalizer.relationType("Notifies About")).isEqualTo("CAUSAL");
        assertThat(canonicalizer.relationType("Governed By")).isEqualTo("CONSTRAINT");
        assertThat(canonicalizer.relationType("unknown custom relation")).isEqualTo("RELATED");
    }

    @Test
    void relationDisplayLabelKeepsShortChinesePredicateOnly() {
        assertThat(canonicalizer.relationDisplayLabel("使用")).isEqualTo("使用");
        assertThat(canonicalizer.relationDisplayLabel(" 复制 到 ")).isEqualTo("复制到");
        assertThat(canonicalizer.relationDisplayLabel("㐀用")).isEqualTo("㐀用");
        assertThat(canonicalizer.relationDisplayLabel("Notifies About")).isEqualTo("相关");
        assertThat(canonicalizer.relationDisplayLabel("")).isEqualTo("相关");
        assertThat(canonicalizer.relationDisplayLabel("这是一个过长的关系描述")).isEqualTo("相关");
    }

    @Test
    void relationDescriptionFallsBackToChineseSentenceWhenModelReturnsEnglish() {
        assertThat(canonicalizer.relationDescription(
                "Redis",
                "Sentinel",
                "使用",
                "Redis uses Sentinel",
                280
        )).isEqualTo("Redis 使用 Sentinel。");
        assertThat(canonicalizer.relationDescription(
                "Redis",
                "Sentinel",
                "使用",
                "Redis 使用 Sentinel 实现故障转移。",
                280
        )).isEqualTo("Redis 使用 Sentinel 实现故障转移。");
    }
}
