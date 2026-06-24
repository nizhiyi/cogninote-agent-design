package com.itqianchen.agentdesign.domain.vo.ingestion;

/**
 * 目录扫描得到的可导入文件快照。
 *
 * <p>健康诊断只需要识别“本地已有但 SQLite 尚未记录”的文件，因此该快照不包含正文内容，
 * 避免可信状态查询变成隐式导入或全文解析。</p>
 */
public record ScannedDocumentFile(
        String documentId,
        String sourcePath,
        String fileName,
        long lastModified
) {
}
