package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Pdf Document 解析器 将来源内容解析为后续 ingestion 可消费的结构。
 * <p>解析结果会进入切块、索引和检索链路，格式稳定性很重要。</p>
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    /**
     * 执行 文档管理 中的 supports 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.PDF;
    }

    /**
     * 解析 parse 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    @Override
    public ParsedDocument parse(Path path) {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setPageEnd("\f");

            String fullText = stripper.getText(document);
            String[] pageTexts = fullText.split("\f", -1);
            List<ParsedSection> sections = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                String pageText = pageIndex < pageTexts.length ? pageTexts[pageIndex] : "";
                if (pageText != null && !pageText.isBlank()) {
                    sections.add(new ParsedSection(pageText, null, pageIndex + 1));
                }
            }

            if (sections.isEmpty()) {
                // 抽取结果为空通常意味着 PDF 没有文本层；当前阶段不做 OCR。
                throw new DocumentParseException("PDF has no extractable text layer: " + path);
            }

            return new ParsedDocument(FileType.PDF, sections);
        } catch (IOException ex) {
            throw new DocumentParseException("Failed to parse PDF file: " + path, ex);
        }
    }
}


