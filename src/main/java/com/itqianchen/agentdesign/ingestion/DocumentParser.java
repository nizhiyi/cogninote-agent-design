package com.itqianchen.agentdesign.ingestion;

import com.itqianchen.agentdesign.document.FileType;
import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(FileType fileType);

    ParsedDocument parse(Path path);
}
