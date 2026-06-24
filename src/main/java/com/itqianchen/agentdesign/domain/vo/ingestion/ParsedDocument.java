package com.itqianchen.agentdesign.domain.vo.ingestion;

import com.itqianchen.agentdesign.domain.enums.document.FileType;
import java.util.List;

/**
 * 文档解析器和切片器之间的统一输出。
 *
 * <p>sections 保留标题、页码和正文，避免 PDF/DOCX/TXT 解析器在下游各自暴露不同结构。</p>
 */
public record ParsedDocument(
        FileType fileType,
        List<ParsedSection> sections
) {
    /**
     * 拼接出降级流程可用的纯文本。
     *
     * <p>空 section 会被忽略，段落之间保留空行，便于哈希、日志和纯文本兜底检索保持稳定口径。</p>
     */
    public String plainText() {
        return sections.stream()
                .map(ParsedSection::content)
                .filter(content -> !content.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }
}


