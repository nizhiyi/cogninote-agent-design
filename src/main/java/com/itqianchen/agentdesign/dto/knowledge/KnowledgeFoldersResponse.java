package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.dto.document.DocumentSummaryResponse;
import java.util.List;

/**
 * Knowledge Folders 响应 定义返回给前端的 知识库 响应结构。
 * <p>该结构属于接口契约，调整字段时需要兼容已有调用方。</p>
 */
public record KnowledgeFoldersResponse(
        List<KnowledgeFolderResponse> folders,
        List<DocumentSummaryResponse> unassignedDocuments
) {
}
