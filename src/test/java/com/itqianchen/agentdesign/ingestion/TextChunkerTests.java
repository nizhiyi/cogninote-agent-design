package com.itqianchen.agentdesign.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTests {

    private final TextChunker textChunker = new TextChunker();

    @Test
    void chunkKeepsHeadingAndCreatesOverlapForPlainText() {
        String text = "a".repeat(TextChunker.MAX_CHUNK_CHARS + 50);
        ParsedDocument document = new ParsedDocument(
                FileType.MARKDOWN,
                List.of(new ParsedSection(text, "Heading", null))
        );

        List<DocumentChunk> chunks = textChunker.chunk(document);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().heading()).isEqualTo("Heading");
        assertThat(chunks.getFirst().content()).hasSize(TextChunker.MAX_CHUNK_CHARS);
        assertThat(chunks.get(1).content()).hasSize(250);
    }

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

        assertThat(cleaned).contains("a\n\nb c");
        assertThat(cleaned).contains("\t\tvoid run()");
        assertThat(cleaned).contains("    System.out.println");
    }

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

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().content()).contains("```java");
        assertThat(chunks.getFirst().content()).contains("    void route()");
        assertThat(chunks.getFirst().content()).contains("```");
    }

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

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks)
                .allSatisfy(chunk -> {
                    assertThat(chunk.content()).startsWith("```mermaid");
                    assertThat(chunk.content()).endsWith("```");
                    assertThat(chunk.content()).contains("flowchart");
                });
    }
}
