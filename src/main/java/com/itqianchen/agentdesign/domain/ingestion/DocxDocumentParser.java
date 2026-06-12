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
 * 解析 DOCX 文档文本。
 *
 * <p>Apache POI 只抽取正文文本，复杂版式和批注不会进入知识库索引。</p>
 */
@Component
public class DocxDocumentParser implements DocumentParser {

    /**
     * 仅接受 OOXML DOCX，避免旧版二进制 DOC 被 POI 以错误格式打开。
     *
     * @param fileType 已识别的文件类型
     * @return 是否由当前解析器处理
     */
    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.DOCX;
    }

    /**
     * 抽取 DOCX 正文文本。
     *
     * <p>Apache POI 不保留复杂版式、批注和修订信息；这些内容不会进入知识库索引。读取失败会包装为
     * DocumentParseException，便于导入流程带着路径定位失败文件。</p>
     *
     * @param path DOCX 文件路径
     * @return 单章节的解析结果
     * @throws DocumentParseException 当文件无法读取或 DOCX 结构无法解析时抛出
     */
    @Override
    public ParsedDocument parse(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return new ParsedDocument(FileType.DOCX, List.of(new ParsedSection(extractor.getText(), null, null)));
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to parse DOCX file: " + path, ex);
        }
    }
}


