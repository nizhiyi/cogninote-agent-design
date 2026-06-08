package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Document 解析器 注册表 根据输入选择合适的 文档管理 实现。
 * <p>注册表让调用方不需要硬编码解析器或处理器类型。</p>
 */
@Component
public class DocumentParserRegistry {

    private final List<DocumentParser> parsers;

    /**
     * 注入 DocumentParserRegistry 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public DocumentParserRegistry(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 解析 parser For 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    public DocumentParser parserFor(FileType fileType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileType))
                .findFirst()
                .orElseThrow(() -> new DocumentParseException("No parser registered for file type: " + fileType));
    }
}


