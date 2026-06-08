package com.itqianchen.agentdesign.domain.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Text Chunker 承担 文档解析 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@Component
public class TextChunker {

    public static final int MAX_CHUNK_CHARS = 1800;
    public static final int OVERLAP_CHARS = 200;
    private static final int ESTIMATED_CHARS_PER_TOKEN = 4;
    private static final Pattern FENCE_MARKER = Pattern.compile("^\\s*```.*$");
    private static final Pattern DIAGRAM_HEADER = Pattern.compile(
            "^\\s*(graph|flowchart|sequenceDiagram|classDiagram|stateDiagram|erDiagram|journey|gantt|pie|mindmap|timeline|@startuml)\\b.*$"
    );

    /**
     * 执行 文档解析 中的 chunk 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public List<DocumentChunk> chunk(ParsedDocument document) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (ParsedSection section : document.sections()) {
            String cleaned = clean(section.content());
            if (cleaned.isBlank()) {
                continue;
            }

            /**
             * 执行 文档解析 中的 split Section 步骤。
             * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
             */
            splitSection(cleaned, section, chunks);
        }

        return chunks;
    }

    /**
     * 执行 文档解析 中的 clean 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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

    /**
     * 执行 文档解析 中的 split Section 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void splitSection(String text, ParsedSection section, List<DocumentChunk> chunks) {
        StringBuilder current = new StringBuilder();
        for (TextBlock block : splitBlocks(text)) {
            String blockText = block.text().trim();
            if (blockText.isBlank()) {
                continue;
            }

            if (blockText.length() > MAX_CHUNK_CHARS) {
                /**
                 * 执行 文档解析 中的 flush Chunk 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                flushChunk(current, section, chunks);
                List<String> oversizedChunks = block.protectedBlock()
                        ? splitOversizedProtectedBlock(blockText)
                        : splitPlainWindow(blockText);
                oversizedChunks.forEach(chunkText -> addChunk(chunkText, section, chunks));
                continue;
            }

            int separatorLength = current.isEmpty() ? 0 : 1;
            if (current.length() + separatorLength + blockText.length() > MAX_CHUNK_CHARS) {
                /**
                 * 执行 文档解析 中的 flush Chunk 步骤。
                 * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                 */
                flushChunk(current, section, chunks);
            }
            /**
             * 追加 append Block 数据。
             * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
             */
            appendBlock(current, blockText);
        }
        /**
         * 执行 文档解析 中的 flush Chunk 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        flushChunk(current, section, chunks);
    }

    /**
     * 执行 文档解析 中的 split Blocks 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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
                    /**
                     * 执行 文档解析 中的 flush Block 步骤。
                     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
                     */
                    flushBlock(blocks, current, false);
                    current.append(line).append('\n');
                    inFence = true;
                }
                continue;
            }

            current.append(line).append('\n');
        }

        /**
         * 执行 文档解析 中的 flush Block 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        flushBlock(blocks, current, inFence);
        return blocks;
    }

    /**
     * 执行 文档解析 中的 clean Plain Text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private String cleanPlainText(String text) {
        return text
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * 执行 文档解析 中的 flush Block 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void flushBlock(List<TextBlock> blocks, StringBuilder current, boolean protectedBlock) {
        if (current.isEmpty()) {
            return;
        }
        blocks.add(new TextBlock(current.toString(), protectedBlock));
        current.setLength(0);
    }

    /**
     * 追加 append Block 数据。
     * <p>追加时维护顺序、状态和关联元数据，保证会话历史可追踪。</p>
     */
    private void appendBlock(StringBuilder current, String blockText) {
        if (!current.isEmpty()) {
            current.append('\n');
        }
        current.append(blockText);
    }

    /**
     * 执行 文档解析 中的 flush Chunk 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void flushChunk(StringBuilder current, ParsedSection section, List<DocumentChunk> chunks) {
        if (current.isEmpty()) {
            return;
        }
        /**
         * 执行 文档解析 中的 add Chunk 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        addChunk(current.toString(), section, chunks);
        current.setLength(0);
    }

    /**
     * 执行 文档解析 中的 add Chunk 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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
                /**
                 * 估算 estimate Token Count 的 token 用量。
                 * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
                 */
                estimateTokenCount(normalizedChunk)
        ));
    }

    /**
     * 执行 文档解析 中的 split Plain Window 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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

    /**
     * 执行 文档解析 中的 split Oversized Protected Block 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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

    /**
     * 执行 文档解析 中的 repeated Protected Headers 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private List<String> repeatedProtectedHeaders(String[] lines) {
        if (lines.length <= 1 || !DIAGRAM_HEADER.matcher(lines[1]).matches()) {
            return List.of();
        }
        // 流程图/时序图这类 fenced block 被拆分后，每段都需要保留图类型声明。
        return List.of(lines[1]);
    }

    /**
     * 执行 文档解析 中的 new Protected Chunk 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private StringBuilder newProtectedChunk(String openingFence, List<String> repeatedHeaders) {
        StringBuilder current = new StringBuilder(openingFence).append('\n');
        for (String header : repeatedHeaders) {
            current.append(header).append('\n');
        }
        return current;
    }

    /**
     * 判断 has Protected Body 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    private boolean hasProtectedBody(StringBuilder current, String openingFence, List<String> repeatedHeaders) {
        int headerLength = openingFence.length() + 1;
        for (String header : repeatedHeaders) {
            headerLength += header.length() + 1;
        }
        return current.length() > headerLength;
    }

    /**
     * 读取 find Closing Fence 对应的数据。
     * <p>缺失、空值和兼容兜底由该方法统一处理。</p>
     */
    private String findClosingFence(String[] lines) {
        if (hasClosingFence(lines)) {
            return lines[lines.length - 1];
        }
        // 未闭合的大代码块也按 fenced block 拆分，补 fence 可以避免 RAG 上下文破坏 Markdown。
        return "```";
    }

    /**
     * 判断 has Closing Fence 条件是否成立。
     * <p>业务判定集中在这里，避免调用方重复实现同一规则。</p>
     */
    private boolean hasClosingFence(String[] lines) {
        return lines.length > 1 && FENCE_MARKER.matcher(lines[lines.length - 1]).matches();
    }

    /**
     * 估算 estimate Token Count 的 token 用量。
     * <p>估算值用于上下文预算、裁剪和前端占用展示。</p>
     */
    private int estimateTokenCount(String text) {
        return (int) Math.ceil((double) text.length() / ESTIMATED_CHARS_PER_TOKEN);
    }

    /**
     * Text Block 是 文档解析 的不可变数据快照。
     * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
     */
    private record TextBlock(String text, boolean protectedBlock) {
    }
}
