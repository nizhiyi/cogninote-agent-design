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
 * 解析 Markdown/TXT 文本文件。
 *
 * <p>Markdown 只抽取第一个 heading 作为章节标题，正文保持原始文本进入切块流程。</p>
 */
@Component
public class TextDocumentParser implements DocumentParser {

    /**
     * 只处理纯文本和 Markdown，二进制文档必须交给对应解析器处理。
     *
     * @param fileType 已识别的文件类型
     * @return 是否由当前解析器处理
     */
    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.MARKDOWN || fileType == FileType.TEXT;
    }

    /**
     * 解析文本文件并保留 Markdown 的首个标题作为章节标题。
     *
     * <p>文件类型会从路径再次校验，避免调用方绕过注册表后把不支持的扩展名送入文本读取流程。</p>
     *
     * @param path Markdown 或 TXT 文件路径
     * @return 单章节的解析结果
     * @throws DocumentParseException 当类型不支持或文件读取失败时抛出
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
     * 读取本地文本内容。
     *
     * <p>优先按 UTF-8 读取；部分历史笔记可能使用系统默认编码，兜底范围限制在解码失败场景，
     * 避免吞掉普通 IO 错误。</p>
     *
     * @param path 文本文件路径
     * @return 文件内容
     * @throws DocumentParseException 当 UTF-8 和默认编码读取都失败时抛出
     */
    private String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException ex) {
            // 一些本地笔记会以 Windows 默认编码保存，首次导入时保留窄范围兜底。
            try {
                return Files.readString(path, Charset.defaultCharset());
            } catch (IOException fallbackEx) {
                throw new DocumentParseException("Failed to read text file with default charset: " + path, fallbackEx);
            }
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to read text file: " + path, ex);
        }
    }

    /**
     * 提取 Markdown 第一处标题作为检索来源标题。
     *
     * <p>这里只服务导入元数据，不参与 Markdown 语义解析；空标题返回 null，让下游按文件名展示。</p>
     *
     * @param content Markdown 原文
     * @return 第一个非空标题，找不到时返回 null
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


