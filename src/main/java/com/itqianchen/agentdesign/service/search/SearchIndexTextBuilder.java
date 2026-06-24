package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.vo.search.IndexedChunk;
import com.itqianchen.agentdesign.domain.vo.search.IndexedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将文档 chunk 拆成适合不同 Lucene analyzer 的索引文本。
 *
 * <p>普通正文走中文分词字段，fenced code / Mermaid / PlantUML 片段走代码字段。
 * 这样既保留自然语言召回，也让路径、类名、方法名和图节点关键字能被精确命中。</p>
 */
@Component
class SearchIndexTextBuilder {

    private static final Pattern FENCE_START = Pattern.compile("^\\s*```\\s*([A-Za-z0-9_+.#-]*)\\s*$");
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:[.:/#\\\\-][A-Za-z0-9_$]+)*"
    );
    private static final Pattern DIAGRAM_KEYWORD = Pattern.compile(
            "\\b(graph|flowchart|sequenceDiagram|classDiagram|stateDiagram|erDiagram|journey|gantt|pie|mindmap|timeline|participant|actor|subgraph|startuml|enduml)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 构造一个 chunk 的双字段索引文本。
     *
     * <p>文件名和标题进入正文索引，代码块内容额外提取标识符变体，避免用户用
     * snake_case、camelCase 或路径片段搜索时漏召回。</p>
     *
     * @param document 文档索引快照
     * @param chunk 文档 chunk
     * @return 正文和代码两个索引字段的文本
     */
    SearchIndexText build(IndexedDocument document, IndexedChunk chunk) {
        List<String> proseParts = new ArrayList<>();
        List<String> codeParts = new ArrayList<>();

        addIfPresent(proseParts, document.fileName());
        addIfPresent(proseParts, chunk.heading());

        for (ContentBlock block : splitBlocks(chunk.content())) {
            if (block.protectedBlock()) {
                addCodeText(codeParts, block.language(), block.text());
            } else {
                addIfPresent(proseParts, block.text());
            }
        }

        return new SearchIndexText(String.join("\n", proseParts), String.join("\n", codeParts));
    }

    /**
     * 将 Markdown 内容拆成普通正文块和 fenced code 块。
     *
     * @param content chunk 原文
     * @return 内容块列表
     */
    private List<ContentBlock> splitBlocks(String content) {
        List<ContentBlock> blocks = new ArrayList<>();
        String normalized = content == null ? "" : content.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder current = new StringBuilder();
        boolean inFence = false;
        String language = "";

        for (String line : normalized.split("\n", -1)) {
            Matcher fence = FENCE_START.matcher(line);
            if (fence.matches()) {
                if (inFence) {
                    current.append(line).append('\n');
                    blocks.add(new ContentBlock(current.toString(), language, true));
                    current.setLength(0);
                    inFence = false;
                    language = "";
                } else {
                    flush(blocks, current, "", false);
                    inFence = true;
                    language = fence.group(1) == null ? "" : fence.group(1).trim();
                    current.append(line).append('\n');
                }
                continue;
            }

            current.append(line).append('\n');
        }

        flush(blocks, current, language, inFence);
        return blocks;
    }

    /**
     * 追加代码索引文本和可拆分标识符。
     *
     * @param parts 代码索引字段片段
     * @param language fenced code 语言
     * @param code 代码块内容
     */
    private void addCodeText(List<String> parts, String language, String code) {
        addIfPresent(parts, language);
        addIfPresent(parts, code);

        // 标识符拆词会扩大召回面，但只写入代码字段，避免污染自然语言 BM25 权重。
        Matcher identifiers = IDENTIFIER.matcher(code == null ? "" : code);
        while (identifiers.find()) {
            String token = identifiers.group();
            addIfPresent(parts, token);
            for (String expanded : expandIdentifier(token)) {
                addIfPresent(parts, expanded);
            }
        }

        // Mermaid/PlantUML 关键字常被用户当作“流程图、时序图”线索搜索，需要显式入索引。
        Matcher diagramKeywords = DIAGRAM_KEYWORD.matcher(code == null ? "" : code);
        while (diagramKeywords.find()) {
            addIfPresent(parts, diagramKeywords.group().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * 将路径、camelCase、snake_case 等标识符拆成可搜索片段。
     *
     * @param token 原始标识符
     * @return 拆分后的片段
     */
    private List<String> expandIdentifier(String token) {
        String normalized = token
                .replace('\\', '/')
                .replace('.', ' ')
                .replace(':', ' ')
                .replace('#', ' ')
                .replace('/', ' ')
                .replace('-', ' ')
                .replace('_', ' ');
        normalized = normalized.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        normalized = normalized.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        String[] parts = normalized.split("\\s+");
        List<String> expanded = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                expanded.add(part);
            }
        }
        return expanded;
    }

    /**
     * 将当前缓存块加入块列表。
     *
     * @param blocks 输出块列表
     * @param current 当前缓存文本
     * @param language fenced code 语言
     * @param protectedBlock 是否为代码/图块
     */
    private void flush(List<ContentBlock> blocks, StringBuilder current, String language, boolean protectedBlock) {
        if (current.isEmpty()) {
            return;
        }
        blocks.add(new ContentBlock(current.toString(), language, protectedBlock));
        current.setLength(0);
    }

    /**
     * 只追加非空文本片段。
     *
     * @param parts 输出片段列表
     * @param value 候选文本
     */
    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value);
        }
    }

    private record ContentBlock(String text, String language, boolean protectedBlock) {
    }
}
