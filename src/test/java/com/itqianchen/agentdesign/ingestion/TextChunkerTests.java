package com.itqianchen.agentdesign.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Text Chunker 测试 承担 文档解析 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class TextChunkerTests {

    private final TextChunker textChunker = new TextChunker();

    /**
     * 执行 文档解析 中的 chunk Keeps Heading And Creates Overlap For Plain Text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chunkKeepsHeadingAndCreatesOverlapForPlainText() {
        String text = "a".repeat(TextChunker.MAX_CHUNK_CHARS + 50);
        ParsedDocument document = new ParsedDocument(
                FileType.MARKDOWN,
                List.of(new ParsedSection(text, "Heading", null))
        );

        List<DocumentChunk> chunks = textChunker.chunk(document);

        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks).hasSize(2);
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks.getFirst().heading()).isEqualTo("Heading");
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks.getFirst().content()).hasSize(TextChunker.MAX_CHUNK_CHARS);
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks.get(1).content()).hasSize(250);
    }

    /**
     * 执行 文档解析 中的 clean Normalizes Plain Text Whitespace But Keeps Code Indentation 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void cleanNormalizesPlainTextWhitespaceButKeepsCodeIndentation() {
        String cleaned = textChunker.clean("a\r\n"
                + "\n"
                + "\n"
                + "b\t\tc\n"
                + "```java\n"
                + "public class Demo {\n"
                + "\t\tvoid run() {\n"
                + "        System.out.println(\"ok\");\n"
                + "    }\n"
                + "}\n"
                + "```\n");

        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(cleaned).contains("a\n\nb c");
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(cleaned).contains("\t\tvoid run()");
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(cleaned).contains("    System.out.println");
    }

    /**
     * 执行 文档解析 中的 chunk Does Not Split Normal Fenced Code Block 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void chunkDoesNotSplitNormalFencedCodeBlock() {
        String markdown = """
                # Agent

                ```java
                /**
                 * Chat 智能体 路由器 根据请求模式路由到对应的 文档解析 实现。
                 * <p>新增模式时优先在路由层扩展，不让调用方散落分支判断。</p>
                 */
                public class ChatAgentRouter {
                    /**
                     * 执行 文档解析 中的 route 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    void route() {
                        useKnowledgeBase = false;
                    }
                }
                ```
                """;
        ParsedDocument document = new ParsedDocument(
                FileType.MARKDOWN,
                List.of(new ParsedSection(markdown, "Agent", null))
        );

        List<DocumentChunk> chunks = textChunker.chunk(document);

        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks).hasSize(1);
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks.getFirst().content()).contains("```java");
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks.getFirst().content()).contains("    void route()");
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks.getFirst().content()).contains("```");
    }

    /**
     * 执行 文档解析 中的 oversized Fenced Block Is Split With Fence Markers Preserved 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void oversizedFencedBlockIsSplitWithFenceMarkersPreserved() {
        String markdown = "```mermaid\n"
                + "flowchart TD\n"
                + ("A --> B\n".repeat(260))
                + "```\n";
        ParsedDocument document = new ParsedDocument(
                FileType.MARKDOWN,
                List.of(new ParsedSection(markdown, "Flow", null))
        );

        List<DocumentChunk> chunks = textChunker.chunk(document);

        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks).hasSizeGreaterThan(1);
        /**
         * 执行 文档解析 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(chunks)
                .allSatisfy(chunk -> {
                    /**
                     * 执行 文档解析 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(chunk.content()).startsWith("```mermaid");
                    /**
                     * 执行 文档解析 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(chunk.content()).endsWith("```");
                    /**
                     * 执行 文档解析 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(chunk.content()).contains("flowchart");
                });
    }
}
