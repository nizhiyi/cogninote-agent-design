package com.itqianchen.agentdesign.domain.support.ingestion;


import com.itqianchen.agentdesign.domain.vo.ingestion.DocumentChunk;
import com.itqianchen.agentdesign.domain.vo.ingestion.ParsedDocument;
import com.itqianchen.agentdesign.domain.vo.ingestion.ParsedSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 将解析后的章节文本切分成可检索的 RAG chunk。
 *
 * <p>普通正文可以带少量重叠来保护语义连续性；代码块、Mermaid 和 PlantUML
 * 这类结构化片段必须作为受保护块处理，避免切分后破坏 Markdown 或图语法。</p>
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
     * 按章节切分文档。
     *
     * @param document 已解析文档，章节顺序会映射为 chunk 顺序
     * @return 可持久化和索引的 chunk 列表
     */
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

    /**
     * 清理正文中的空白噪声，同时保留 fenced block 的原始结构。
     *
     * @param text 解析器输出的章节文本
     * @return 可用于切片的规范化文本
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
     * 将单个章节切成一个或多个 chunk。
     *
     * <p>普通正文按长度聚合，超过上限时使用带重叠的滑窗；受保护块在超过上限时单独拆分，
     * 避免代码 fence、Mermaid 或 PlantUML 语法被普通窗口切断。</p>
     *
     * @param text 已清理的章节文本
     * @param section 章节元数据，页码和标题会复制到每个 chunk
     * @param chunks 输出列表，chunkIndex 按当前列表长度递增
     */
    private void splitSection(String text, ParsedSection section, List<DocumentChunk> chunks) {
        StringBuilder current = new StringBuilder();
        for (TextBlock block : splitBlocks(text)) {
            String blockText = block.text().trim();
            if (blockText.isBlank()) {
                continue;
            }

            if (blockText.length() > MAX_CHUNK_CHARS) {
                flushChunk(current, section, chunks);
                // 受保护块不能像正文一样任意滑窗，否则代码 fence 或图声明会被切坏。
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

    /**
     * 按 fenced block 边界拆分文本块。
     *
     * <p>受保护块必须保持原始换行和 fence 标记；普通正文后续会继续做空白规范化。</p>
     *
     * @param text 规范化换行后的章节文本
     * @return 按原始顺序排列的普通块和受保护块
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

    /**
     * 清理普通正文中的空白噪声。
     *
     * <p>这里只处理非 fenced block 文本，避免把代码缩进、图语法换行或表格格式压坏。</p>
     *
     * @param text 普通正文块
     * @return 保留段落边界的清理结果
     */
    private String cleanPlainText(String text) {
        return text
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" {2,}", " ")
                .replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * 将当前累计文本写入块列表。
     *
     * <p>调用方负责在进入或离开 fenced block 时传入正确的 protectedBlock 标记，
     * 该标记决定后续是否允许压缩空白或普通滑窗切分。</p>
     *
     * @param blocks 输出块列表
     * @param current 当前累计文本
     * @param protectedBlock 当前块是否需要保留结构
     */
    private void flushBlock(List<TextBlock> blocks, StringBuilder current, boolean protectedBlock) {
        if (current.isEmpty()) {
            return;
        }
        blocks.add(new TextBlock(current.toString(), protectedBlock));
        current.setLength(0);
    }

    /**
     * 将一个文本块追加到当前 chunk 缓冲区。
     *
     * <p>块之间固定补一个换行，保证相邻段落或代码块不会粘连成新的 Markdown 语义。</p>
     *
     * @param current 当前 chunk 缓冲区
     * @param blockText 待追加文本块
     */
    private void appendBlock(StringBuilder current, String blockText) {
        if (!current.isEmpty()) {
            current.append('\n');
        }
        current.append(blockText);
    }

    /**
     * 将当前 chunk 缓冲区落入输出列表。
     *
     * <p>chunk 序号依赖输出列表长度，因此该方法必须在所有新增 chunk 的统一入口上调用。</p>
     *
     * @param current 当前 chunk 缓冲区
     * @param section 章节元数据
     * @param chunks 输出 chunk 列表
     */
    private void flushChunk(StringBuilder current, ParsedSection section, List<DocumentChunk> chunks) {
        if (current.isEmpty()) {
            return;
        }
        addChunk(current.toString(), section, chunks);
        current.setLength(0);
    }

    /**
     * 创建带章节来源和 token 估算的 DocumentChunk。
     *
     * <p>空白片段会被丢弃，避免解析器产生空 chunk 后污染检索排序和上下文预算。</p>
     *
     * @param chunkText chunk 正文
     * @param section 章节元数据
     * @param chunks 输出 chunk 列表
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
                estimateTokenCount(normalizedChunk)
        ));
    }

    /**
     * 使用重叠窗口切分超长普通正文。
     *
     * <p>重叠只用于自然语言文本，目的是减少段落边界处的语义丢失；受保护块不能走这个路径。</p>
     *
     * @param text 超过单 chunk 长度上限的普通正文
     * @return 可直接写入 chunk 的正文片段
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
     * 切分超长 fenced block 并为每段补齐可独立渲染的结构。
     *
     * <p>每个子块都会保留 opening fence、必要的图声明和 closing fence，
     * 这样前端 Markdown/Mermaid 渲染和 RAG 引用都能把片段当成完整代码块处理。</p>
     *
     * @param text 超过单 chunk 长度上限的受保护块
     * @return 独立闭合的受保护块片段
     */
    private List<String> splitOversizedProtectedBlock(String text) {
        String[] lines = text.split("\n", -1);
        String openingFence = lines.length == 0 ? "```" : lines[0];
        String closingFence = findClosingFence(lines);
        List<String> chunks = new ArrayList<>();
        List<String> repeatedHeaders = repeatedProtectedHeaders(lines);
        // 每个子块都重新带上 fence 和图声明，前端 Markdown/Mermaid 渲染才不会把片段当成坏文本。
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
     * 提取拆分后必须重复到每个子块的图声明行。
     *
     * <p>Mermaid 和 PlantUML 的图类型声明通常在 fence 后第一行；缺失该行时，
     * 后续片段会被渲染器当成普通代码或无效图语法。</p>
     *
     * @param lines 受保护块按行拆分后的内容
     * @return 需要复制到每个子块开头的声明行
     */
    private List<String> repeatedProtectedHeaders(String[] lines) {
        if (lines.length <= 1 || !DIAGRAM_HEADER.matcher(lines[1]).matches()) {
            return List.of();
        }
        // 流程图/时序图这类 fenced block 被拆分后，每段都需要保留图类型声明。
        return List.of(lines[1]);
    }

    /**
     * 创建一个带 opening fence 和重复声明行的受保护块缓冲区。
     *
     * @param openingFence 原始 opening fence
     * @param repeatedHeaders 每个子块都需要保留的图声明行
     * @return 已写入头部结构的缓冲区
     */
    private StringBuilder newProtectedChunk(String openingFence, List<String> repeatedHeaders) {
        StringBuilder current = new StringBuilder(openingFence).append('\n');
        for (String header : repeatedHeaders) {
            current.append(header).append('\n');
        }
        return current;
    }

    /**
     * 判断受保护块缓冲区是否已经包含真实内容。
     *
     * <p>只包含 fence 和重复声明行的缓冲区不能生成 chunk，否则会产生无意义的空图块。</p>
     *
     * @param current 受保护块缓冲区
     * @param openingFence 原始 opening fence
     * @param repeatedHeaders 每个子块都需要保留的图声明行
     * @return 是否存在头部之外的正文
     */
    private boolean hasProtectedBody(StringBuilder current, String openingFence, List<String> repeatedHeaders) {
        int headerLength = openingFence.length() + 1;
        for (String header : repeatedHeaders) {
            headerLength += header.length() + 1;
        }
        return current.length() > headerLength;
    }

    /**
     * 找到受保护块的 closing fence。
     *
     * <p>解析到未闭合代码块时补默认 fence，让拆出的片段仍然能被 Markdown 解析器安全消费。</p>
     *
     * @param lines 受保护块按行拆分后的内容
     * @return 原始 closing fence 或默认 closing fence
     */
    private String findClosingFence(String[] lines) {
        if (hasClosingFence(lines)) {
            return lines[lines.length - 1];
        }
        // 未闭合的大代码块也按 fenced block 拆分，补 fence 可以避免 RAG 上下文破坏 Markdown。
        return "```";
    }

    /**
     * 判断受保护块最后一行是否是 closing fence。
     *
     * @param lines 受保护块按行拆分后的内容
     * @return 是否存在显式 closing fence
     */
    private boolean hasClosingFence(String[] lines) {
        return lines.length > 1 && FENCE_MARKER.matcher(lines[lines.length - 1]).matches();
    }

    /**
     * 使用字符数估算 token 数量。
     *
     * <p>这里的估算只用于上下文预算展示和排序辅助，不能作为模型 provider 的精确计费口径。</p>
     *
     * @param text chunk 文本
     * @return 估算 token 数
     */
    private int estimateTokenCount(String text) {
        return (int) Math.ceil((double) text.length() / ESTIMATED_CHARS_PER_TOKEN);
    }

    /**
     * 文本切块前的中间块。
     *
     * <p>protectedBlock 表示该块必须保留结构，后续不能执行普通正文的空白压缩和滑窗切分。</p>
     */
    private record TextBlock(String text, boolean protectedBlock) {
    }
}
