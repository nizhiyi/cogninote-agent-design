package com.itqianchen.agentdesign.domain.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    public static final int MAX_CHUNK_CHARS = 1800;
    public static final int OVERLAP_CHARS = 200;
    private static final int ESTIMATED_CHARS_PER_TOKEN = 4;
    private static final Pattern FENCE_MARKER = Pattern.compile("^\\s*```.*$");
    private static final Pattern DIAGRAM_HEADER = Pattern.compile(
            "^\\s*(graph|flowchart|sequenceDiagram|classDiagram|stateDiagram|erDiagram|journey|gantt|pie|mindmap|timeline|@startuml)\\b.*$"
    );

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
        String normalized = text
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder();
        for (TextBlock block : splitBlocks(normalized)) {
            cleaned.append(block.protectedBlock() ? block.text() : cleanPlainText(block.text()));
        }
        return cleaned.toString().trim();
    }

    private void splitSection(String text, ParsedSection section, List<DocumentChunk> chunks) {
        StringBuilder current = new StringBuilder();
        for (TextBlock block : splitBlocks(text)) {
            String blockText = block.text().trim();
            if (blockText.isBlank()) {
                continue;
            }

            if (blockText.length() > MAX_CHUNK_CHARS) {
                flushChunk(current, section, chunks);
                List<String> oversizedChunks = block.protectedBlock()
                        ? splitOversizedProtectedBlock(blockText)
                        : splitPlainWindow(blockText);
                oversizedChunks.forEach(chunkText -> addChunk(chunkText, section, chunks));
                continue;
            }

            int separatorLength = current.isEmpty() ? 0 : 1;
            if (current.length() + separatorLength + blockText.length() > MAX_CHUNK_CHARS) {
                flushChunk(current, section, chunks);
            }
            appendBlock(current, blockText);
        }
        flushChunk(current, section, chunks);
    }

    private List<TextBlock> splitBlocks(String text) {
        List<TextBlock> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inFence = false;

        for (String line : text.split("\n", -1)) {
            if (FENCE_MARKER.matcher(line).matches()) {
                if (inFence) {
                    current.append(line).append('\n');
                    blocks.add(new TextBlock(current.toString(), true));
                    current.setLength(0);
                    inFence = false;
                } else {
                    flushBlock(blocks, current, false);
                    current.append(line).append('\n');
                    inFence = true;
                }
                continue;
            }

            current.append(line).append('\n');
        }

        flushBlock(blocks, current, inFence);
        return blocks;
    }

    private String cleanPlainText(String text) {
        return text
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n");
    }

    private void flushBlock(List<TextBlock> blocks, StringBuilder current, boolean protectedBlock) {
        if (current.isEmpty()) {
            return;
        }
        blocks.add(new TextBlock(current.toString(), protectedBlock));
        current.setLength(0);
    }

    private void appendBlock(StringBuilder current, String blockText) {
        if (!current.isEmpty()) {
            current.append('\n');
        }
        current.append(blockText);
    }

    private void flushChunk(StringBuilder current, ParsedSection section, List<DocumentChunk> chunks) {
        if (current.isEmpty()) {
            return;
        }
        addChunk(current.toString(), section, chunks);
        current.setLength(0);
    }

    private void addChunk(String chunkText, ParsedSection section, List<DocumentChunk> chunks) {
        String normalizedChunk = chunkText.trim();
        if (normalizedChunk.isBlank()) {
            return;
        }
        chunks.add(new DocumentChunk(
                chunks.size(),
                normalizedChunk,
                section.pageNumber(),
                section.heading(),
                estimateTokenCount(normalizedChunk)
        ));
    }

    private List<String> splitPlainWindow(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
            String chunkText = text.substring(start, end);
            if (!chunkText.trim().isBlank()) {
                chunks.add(chunkText);
            }

            if (end >= text.length()) {
                break;
            }

            // 普通正文保留少量重叠，避免 RAG 在自然语言段落边界丢语义；代码块不走重叠窗口。
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return chunks;
    }

    private List<String> splitOversizedProtectedBlock(String text) {
        String[] lines = text.split("\n", -1);
        String openingFence = lines.length == 0 ? "```" : lines[0];
        String closingFence = findClosingFence(lines);
        List<String> chunks = new ArrayList<>();
        List<String> repeatedHeaders = repeatedProtectedHeaders(lines);
        int contentStart = 1 + repeatedHeaders.size();
        int contentEnd = hasClosingFence(lines) ? lines.length - 1 : lines.length;
        StringBuilder current = newProtectedChunk(openingFence, repeatedHeaders);

        for (int i = contentStart; i < contentEnd; i++) {
            String line = lines[i];

            int projectedLength = current.length() + line.length() + 1 + closingFence.length() + 1;
            if (projectedLength > MAX_CHUNK_CHARS && hasProtectedBody(current, openingFence, repeatedHeaders)) {
                current.append(closingFence);
                chunks.add(current.toString());
                current = newProtectedChunk(openingFence, repeatedHeaders);
            }
            current.append(line).append('\n');
        }

        if (hasProtectedBody(current, openingFence, repeatedHeaders)) {
            current.append(closingFence);
            chunks.add(current.toString());
        }
        return chunks;
    }

    private List<String> repeatedProtectedHeaders(String[] lines) {
        if (lines.length <= 1 || !DIAGRAM_HEADER.matcher(lines[1]).matches()) {
            return List.of();
        }
        // 流程图/时序图这类 fenced block 被拆分后，每段都需要保留图类型声明。
        return List.of(lines[1]);
    }

    private StringBuilder newProtectedChunk(String openingFence, List<String> repeatedHeaders) {
        StringBuilder current = new StringBuilder(openingFence).append('\n');
        for (String header : repeatedHeaders) {
            current.append(header).append('\n');
        }
        return current;
    }

    private boolean hasProtectedBody(StringBuilder current, String openingFence, List<String> repeatedHeaders) {
        int headerLength = openingFence.length() + 1;
        for (String header : repeatedHeaders) {
            headerLength += header.length() + 1;
        }
        return current.length() > headerLength;
    }

    private String findClosingFence(String[] lines) {
        if (hasClosingFence(lines)) {
            return lines[lines.length - 1];
        }
        // 未闭合的大代码块也按 fenced block 拆分，补 fence 可以避免 RAG 上下文破坏 Markdown。
        return "```";
    }

    private boolean hasClosingFence(String[] lines) {
        return lines.length > 1 && FENCE_MARKER.matcher(lines[lines.length - 1]).matches();
    }

    private int estimateTokenCount(String text) {
        return (int) Math.ceil((double) text.length() / ESTIMATED_CHARS_PER_TOKEN);
    }

    private record TextBlock(String text, boolean protectedBlock) {
    }
}
