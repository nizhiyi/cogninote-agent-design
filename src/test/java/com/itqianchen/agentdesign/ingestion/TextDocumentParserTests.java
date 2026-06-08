package com.itqianchen.agentdesign.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;

import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import java.nio.file.Files;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import java.nio.file.Path;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import org.junit.jupiter.api.Test;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import org.junit.jupiter.api.io.TempDir;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextDocumentParser;

/**
 * Text Document 解析器 测试 承担 文档管理 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
class TextDocumentParserTests {

    private final TextDocumentParser parser = new TextDocumentParser();

    @TempDir
    private Path tempDir;

    /**
     * 解析 parse Markdown Reads Heading 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    @Test
    void parseMarkdownReadsHeading() throws Exception {
        Path markdown = tempDir.resolve("note.md");
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        Files.writeString(markdown, "# Title\n\nBody");

        ParsedDocument document = parser.parse(markdown);

        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(document.fileType()).isEqualTo(FileType.MARKDOWN);
        /**
         * 执行 文档管理 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(document.sections()).singleElement()
                .satisfies(section -> {
                    /**
                     * 执行 文档管理 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(section.heading()).isEqualTo("Title");
                    /**
                     * 执行 文档管理 中的 assert That 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    assertThat(section.content()).contains("Body");
                });
    }
}


