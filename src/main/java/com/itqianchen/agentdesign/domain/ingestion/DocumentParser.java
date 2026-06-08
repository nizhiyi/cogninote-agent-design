package com.itqianchen.agentdesign.domain.ingestion;

import com.itqianchen.agentdesign.domain.document.FileType;
import java.nio.file.Path;

/**
 * Document 解析器 将来源内容解析为后续 ingestion 可消费的结构。
 * <p>解析结果会进入切块、索引和检索链路，格式稳定性很重要。</p>
 */
public interface DocumentParser {

    /**
     * 执行 文档管理 中的 supports 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    boolean supports(FileType fileType);

    /**
     * 解析 parse 输入。
     * <p>将外部文本或结构转换为模块内部可直接使用的对象。</p>
     */
    ParsedDocument parse(Path path);
}


