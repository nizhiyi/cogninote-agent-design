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

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.MARKDOWN || fileType == FileType.TEXT;
    }

    @Override
    public ParsedDocument parse(Path path) {
        FileType fileType = FileType.fromFileName(path.getFileName().toString())
                .orElseThrow(() -> new DocumentParseException("Unsupported text file type: " + path));
        String content = readText(path);
        String heading = fileType == FileType.MARKDOWN ? firstMarkdownHeading(content) : null;
        return new ParsedDocument(fileType, List.of(new ParsedSection(content, heading, null)));
    }

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


