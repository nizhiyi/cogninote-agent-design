package com.itqianchen.agentdesign.document;

import java.util.Locale;
import java.util.Optional;

public enum FileType {
    MARKDOWN(".md"),
    TEXT(".txt"),
    DOCX(".docx"),
    PDF(".pdf");

    private final String extension;

    FileType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static Optional<FileType> fromFileName(String fileName) {
        String lowerFileName = fileName.toLowerCase(Locale.ROOT);
        for (FileType fileType : values()) {
            if (lowerFileName.endsWith(fileType.extension)) {
                return Optional.of(fileType);
            }
        }

        return Optional.empty();
    }
}
