package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
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


