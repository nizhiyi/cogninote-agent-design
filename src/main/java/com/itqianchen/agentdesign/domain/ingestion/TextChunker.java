package com.itqianchen.agentdesign.domain.ingestion;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    public static final int MAX_CHUNK_CHARS = 1800;
    public static final int OVERLAP_CHARS = 200;
    private static final int ESTIMATED_CHARS_PER_TOKEN = 4;

    public List<DocumentChunk> chunk(ParsedDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (ParsedSection section : document.sections()) {
            String cleaned = clean(section.content());
            if (cleaned.isBlank()) {
                continue;
            }

            splitSection(cleaned, section, chunks);
        }

        return chunks;
    }

    public String clean(String text) {
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private void splitSection(String text, ParsedSection section, List<DocumentChunk> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isBlank()) {
                chunks.add(new DocumentChunk(
                        chunks.size(),
                        chunkText,
                        section.pageNumber(),
                        section.heading(),
                        estimateTokenCount(chunkText)
                ));
            }

            if (end >= text.length()) {
                break;
            }

            // 保留少量重叠文本，后续 RAG 组装上下文时不容易在段落边界丢失语义。
            // 当前阶段不引入 tokenizer，所以先用字符级窗口做稳定近似。
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
    }

    private int estimateTokenCount(String text) {
        return (int) Math.ceil((double) text.length() / ESTIMATED_CHARS_PER_TOKEN);
    }
}


