package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * Docx Document 解析器 将来源内容解析为后续 ingestion 可消费的结构。
 * <p>解析结果会进入切块、索引和检索链路，格式稳定性很重要。</p>
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    /**
     * 执行 文档管理 中的 supports 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.DOCX;
    }

    /**
     * 解析 parse 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    @Override
    public ParsedDocument parse(Path path) {
        // 文件系统访问可能抛出 IO 异常，调用方需要保留失败上下文。
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return new ParsedDocument(FileType.DOCX, List.of(new ParsedSection(extractor.getText(), null, null)));
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to parse DOCX file: " + path, ex);
        }
    }
}


