package com.itqianchen.agentdesign.dto.knowledge;

import com.itqianchen.agentdesign.dto.document.DocumentSummaryResponse;
import java.util.List;

public record KnowledgeFoldersResponse(
        List<KnowledgeFolderResponse> folders,
        List<DocumentSummaryResponse> unassignedDocuments
) {
}
