package com.itqianchen.agentdesign.controller.document;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.dto.document.DocumentSummaryResponse;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsRequest;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.service.document.DocumentIngestionService;
import com.itqianchen.agentdesign.service.document.DocumentManagementService;
import com.itqianchen.agentdesign.service.document.DocumentQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentManagementService documentManagementService;
    private final DocumentQueryService documentQueryService;

    public DocumentController(
            DocumentIngestionService ingestionService,
            DocumentManagementService documentManagementService,
            DocumentQueryService documentQueryService
    ) {
        this.ingestionService = ingestionService;
        this.documentManagementService = documentManagementService;
        this.documentQueryService = documentQueryService;
    }

    @GetMapping
    public ApiResponse<List<DocumentSummaryResponse>> listDocuments() {
        return ApiResponse.ok(documentQueryService.listDocuments());
    }

    @PostMapping("/ingest")
    public ApiResponse<IngestDocumentsResponse> ingest(@Valid @RequestBody IngestDocumentsRequest request) {
        return ApiResponse.ok(ingestionService.ingestFolder(request.folderPath(), request.recursiveOrDefault()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        if (!documentManagementService.deleteDocument(id)) {
            throw new ResourceNotFoundException("Document not found: " + id);
        }
        return ResponseEntity.noContent().build();
    }
}


