package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.util.List;

/**
 * Parsed Document 是 文档管理 的不可变数据快照。
 * <p>record 用于跨层传递数据，不承载可变业务状态。</p>
 */
public record ParsedDocument(
        FileType fileType,
        List<ParsedSection> sections
) {
    /**
     * 执行 文档管理 中的 plain Text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String plainText() {
        return sections.stream()
                .map(ParsedSection::content)
                .filter(content -> !content.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}


