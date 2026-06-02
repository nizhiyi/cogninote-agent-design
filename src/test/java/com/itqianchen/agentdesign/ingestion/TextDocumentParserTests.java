package com.itqianchen.agentdesign.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.itqianchen.agentdesign.document.FileType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextDocumentParserTests {

    private final TextDocumentParser parser = new TextDocumentParser();

    @TempDir
    private Path tempDir;

    @Test
    void parseMarkdownReadsHeading() throws Exception {
        Path markdown = tempDir.resolve("note.md");
        Files.writeString(markdown, "# Title\n\nBody");

        ParsedDocument document = parser.parse(markdown);

        assertThat(document.fileType()).isEqualTo(FileType.MARKDOWN);
        assertThat(document.sections()).singleElement()
                .satisfies(section -> {
                    assertThat(section.heading()).isEqualTo("Title");
                    assertThat(section.content()).contains("Body");
                });
    }
}
