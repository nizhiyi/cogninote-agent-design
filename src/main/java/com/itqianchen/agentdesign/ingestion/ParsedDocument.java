package com.itqianchen.agentdesign.ingestion;

import com.itqianchen.agentdesign.document.FileType;
import java.util.List;

public record ParsedDocument(
        FileType fileType,
        List<ParsedSection> sections
) {
    public String plainText() {
        return sections.stream()
                .map(ParsedSection::content)
                .filter(content -> !content.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}
