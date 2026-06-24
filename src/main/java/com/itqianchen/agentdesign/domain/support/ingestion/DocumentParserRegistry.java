package com.itqianchen.agentdesign.domain.support.ingestion;


import com.itqianchen.agentdesign.domain.exception.ingestion.DocumentParseException;
import com.itqianchen.agentdesign.domain.interfaces.ingestion.DocumentParser;
import com.itqianchen.agentdesign.domain.enums.document.FileType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 文档解析器注册表。
 *
 * <p>解析器由 Spring 注入，按 FileType 选择第一个匹配实现；新增文件类型时必须同时注册解析器，
 * 否则导入流程会在这里快速失败。</p>
 */
@Component
public class DocumentParserRegistry {

    private final List<DocumentParser> parsers;

    /**
     * 注入所有解析器实现。
     *
     * <p>Spring 的注入顺序只影响同一 FileType 多实现时的优先级；正常情况下每种类型应只有一个实现。</p>
     *
     * @param parsers 由 Spring 收集的解析器列表
     */
    public DocumentParserRegistry(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 根据 FileType 选择解析器。
     *
     * <p>不支持的类型在导入入口已经过滤；这里失败意味着装配或枚举映射不一致。</p>
     */
    public DocumentParser parserFor(FileType fileType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new DocumentParseException("No parser registered for file type: " + fileType));
    }
}


