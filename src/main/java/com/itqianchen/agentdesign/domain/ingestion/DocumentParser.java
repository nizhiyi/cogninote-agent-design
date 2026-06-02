package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(FileType fileType);

    ParsedDocument parse(Path path);
}


