package com.itqianchen.agentdesign.domain.document;

import java.util.Locale;
import java.util.Optional;

/**
 * File Type 枚举 文档管理 的稳定取值。
 * <p>枚举值可能进入数据库或 API 响应，修改时需要考虑兼容性。</p>
 */
public enum FileType {
    MARKDOWN(".md"),
    TEXT(".txt"),
    DOCX(".docx"),
    /**
     * 执行 文档管理 中的 PDF 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    PDF(".pdf");

    private final String extension;

    /**
     * 注入 FileType 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    FileType(String extension) {
        this.extension = extension;
    }

    /**
     * 执行 文档管理 中的 extension 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    public String extension() {
        return extension;
    }

    /**
     * 执行 文档管理 中的 from File Name 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
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


