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

@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(FileType fileType) {
        return fileType == FileType.DOCX;
    }

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


