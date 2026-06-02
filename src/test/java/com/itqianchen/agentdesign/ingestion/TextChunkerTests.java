package com.itqianchen.agentdesign.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;

import com.itqianchen.agentdesign.domain.document.FileType;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import java.util.List;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import org.junit.jupiter.api.Test;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;
import com.itqianchen.agentdesign.domain.ingestion.ParsedSection;
import com.itqianchen.agentdesign.domain.ingestion.TextChunker;

class TextChunkerTests {

    private final TextChunker textChunker = new TextChunker();

    @Test
    void chunkKeepsHeadingAndCreatesOverlap() {
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
    void cleanNormalizesWhitespace() {
        String cleaned = textChunker.clean("a\r\n\r\n\r\nb\t\tc");

        assertThat(cleaned).isEqualTo("a\n\nb c");
    }
}


