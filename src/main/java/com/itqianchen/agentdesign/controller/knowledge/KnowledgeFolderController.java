package com.itqianchen.agentdesign.controller.knowledge;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.dto.document.IngestDocumentsResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderEnabledRequest;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderImportRequest;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderRebuildResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFoldersResponse;
import com.itqianchen.agentdesign.service.knowledge.KnowledgeFolderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge-folders")
public class KnowledgeFolderController {

    private final KnowledgeFolderService knowledgeFolderService;

    public KnowledgeFolderController(KnowledgeFolderService knowledgeFolderService) {
        this.knowledgeFolderService = knowledgeFolderService;
    }

    @GetMapping
    public ApiResponse<KnowledgeFoldersResponse> listFolders() {
        return ApiResponse.ok(knowledgeFolderService.listFolders());
    }

    @PostMapping("/import")
    public ApiResponse<IngestDocumentsResponse> importFolder(
            @Valid @RequestBody KnowledgeFolderImportRequest request
    ) {
        return ApiResponse.ok(knowledgeFolderService.importFolder(
                request.folderPath(),
                request.recursiveOrDefault()
        ));
    }

    @PostMapping("/{id}/rebuild")
    public ApiResponse<KnowledgeFolderRebuildResponse> rebuildFolder(@PathVariable String id) {
        return ApiResponse.ok(knowledgeFolderService.rebuildFolder(id));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Void> setEnabled(
            @PathVariable String id,
            @Valid @RequestBody KnowledgeFolderEnabledRequest request
    ) {
        knowledgeFolderService.setEnabled(id, request.enabled());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) {
        knowledgeFolderService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
