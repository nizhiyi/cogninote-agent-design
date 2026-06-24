package com.itqianchen.agentdesign.domain.enums.document;

import java.util.Locale;
import java.util.Optional;

/**
 * 文档导入支持的文件类型。
 *
 * <p>枚举值可能进入数据库或 API 响应，新增或改名时需要同步解析器注册和前端展示。</p>
 */
public enum FileType {
    /** Markdown 文档。 */
    MARKDOWN(".md"),

    /** 纯文本或日志类文档。 */
    TEXT(".txt"),

    /** Word OpenXML 文档。 */
    DOCX(".docx"),

    /** PDF 文档。 */
    PDF(".pdf");

    private final String extension;

    /**
     * 绑定文件类型和扩展名。
     *
     * @param extension 小写扩展名，包含前导点
     */
    FileType(String extension) {
        this.extension = extension;
    }

    /**
     * 返回用于文件名匹配的扩展名。
     *
     * @return 小写扩展名，包含前导点
     */
    public String extension() {
        return extension;
    }

    /**
     * 根据文件名后缀解析支持的文档类型。
     *
     * <p>匹配使用 ROOT locale，避免土耳其语等区域设置影响大小写转换。</p>
     *
     * @param fileName 本地文件名或路径
     * @return 支持的文件类型；不支持时为空
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


