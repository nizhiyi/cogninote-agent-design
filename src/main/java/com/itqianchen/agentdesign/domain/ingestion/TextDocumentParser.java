package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Text Document 解析器 将来源内容解析为后续 ingestion 可消费的结构。
 * <p>解析结果会进入切块、索引和检索链路，格式稳定性很重要。</p>
 */
@Component
public class TextDocumentParser implements DocumentParser {

    /**
     * 执行 文档管理 中的 supports 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.MARKDOWN || fileType == FileType.TEXT;
    }

    /**
     * 解析 parse 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    @Override
    public ParsedDocument parse(Path path) {
        FileType fileType = FileType.fromFileName(path.getFileName().toString())
                .orElseThrow(() -> new DocumentParseException("Unsupported text file type: " + path));
        String content = readText(path);
        String heading = fileType == FileType.MARKDOWN ? firstMarkdownHeading(content) : null;
        return new ParsedDocument(fileType, List.of(new ParsedSection(content, heading, null)));
    }

    /**
     * 执行 文档管理 中的 read Text 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private String readText(Path path) {
        try {
            // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ex) {
            // 一些本地笔记会以 Windows 默认编码保存，首次导入时保留窄范围兜底。
            try {
                // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
                return Files.readString(path, Charset.defaultCharset());
            } catch (IOException fallbackEx) {
                throw new DocumentParseException("Failed to read text file with default charset: " + path, fallbackEx);
            }
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to read text file: " + path, ex);
        }
    }

    /**
     * 执行 文档管理 中的 first Markdown Heading 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private String firstMarkdownHeading(String content) {
        return content.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("#"))
                .map(line -> line.replaceFirst("^#+\\s*", "").trim())
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(null);
    }
}


