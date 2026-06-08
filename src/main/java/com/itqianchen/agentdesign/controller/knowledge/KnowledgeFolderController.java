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

/**
 * Knowledge Folder 控制器 暴露 知识库 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
@RequestMapping("/api/knowledge-folders")
public class KnowledgeFolderController {

    private final KnowledgeFolderService knowledgeFolderService;

    /**
     * 注入 KnowledgeFolderController 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public KnowledgeFolderController(KnowledgeFolderService knowledgeFolderService) {
        this.knowledgeFolderService = knowledgeFolderService;
    }

    /**
     * 查询 知识库 列表。
     * <p>返回值面向上层展示或接口响应，不暴露底层存储细节。</p>
     */
    @GetMapping
    public ApiResponse<KnowledgeFoldersResponse> listFolders() {
        return ApiResponse.ok(knowledgeFolderService.listFolders());
    }

    /**
     * 执行 知识库 中的 import Folder 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @PostMapping("/import")
    public ApiResponse<IngestDocumentsResponse> importFolder(
            @Valid @RequestBody KnowledgeFolderImportRequest request
    ) {
        return ApiResponse.ok(knowledgeFolderService.importFolder(
                request.folderPath(),
                request.recursiveOrDefault()
        ));
    }

    /**
     * 执行 知识库 中的 rebuild Folder 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @PostMapping("/{id}/rebuild")
    public ApiResponse<KnowledgeFolderRebuildResponse> rebuildFolder(@PathVariable String id) {
        return ApiResponse.ok(knowledgeFolderService.rebuildFolder(id));
    }

    /**
     * 设置 set Enabled 状态。
     * <p>状态变更会同步维护当前模块需要的派生信息。</p>
     */
    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Void> setEnabled(
            @PathVariable String id,
            @Valid @RequestBody KnowledgeFolderEnabledRequest request
    ) {
        knowledgeFolderService.setEnabled(id, request.enabled());
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除 delete Folder 对应的数据。
     * <p>删除时同步处理关联状态，避免调用方遗漏清理步骤。</p>
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) {
        knowledgeFolderService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
