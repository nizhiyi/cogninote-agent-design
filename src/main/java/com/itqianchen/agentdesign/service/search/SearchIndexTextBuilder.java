package com.itqianchen.agentdesign.service.search;

import com.itqianchen.agentdesign.domain.search.IndexedChunk;
import com.itqianchen.agentdesign.domain.search.IndexedDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    private void addCodeText(List<String> parts, String language, String code) {
        addIfPresent(parts, language);
        addIfPresent(parts, code);

        Matcher identifiers = IDENTIFIER.matcher(code == null ? "" : code);
        while (identifiers.find()) {
            String token = identifiers.group();
            addIfPresent(parts, token);
            for (String expanded : expandIdentifier(token)) {
                addIfPresent(parts, expanded);
            }
        }

        Matcher diagramKeywords = DIAGRAM_KEYWORD.matcher(code == null ? "" : code);
        while (diagramKeywords.find()) {
            addIfPresent(parts, diagramKeywords.group().toLowerCase(Locale.ROOT));
        }
    }

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

    private void flush(List<ContentBlock> blocks, StringBuilder current, String language, boolean protectedBlock) {
        if (current.isEmpty()) {
            return;
        }
        blocks.add(new ContentBlock(current.toString(), language, protectedBlock));
        current.setLength(0);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(value);
        }
    }

    private record ContentBlock(String text, String language, boolean protectedBlock) {
    }
}
