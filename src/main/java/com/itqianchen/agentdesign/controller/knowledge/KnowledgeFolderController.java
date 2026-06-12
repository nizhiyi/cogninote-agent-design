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
     * 注入知识库目录服务。
     *
     * @param knowledgeFolderService 目录导入、重建和可见性服务
     */
    public KnowledgeFolderController(KnowledgeFolderService knowledgeFolderService) {
        this.knowledgeFolderService = knowledgeFolderService;
    }

    /**
     * 返回知识库目录和未归属文档的管理视图。
     *
     * @return 知识库目录响应
     */
    @GetMapping
    public ApiResponse<KnowledgeFoldersResponse> listFolders() {
        return ApiResponse.ok(knowledgeFolderService.listFolders());
    }

    /**
     * 导入一个本地目录为知识库。
     *
     * <p>导入会创建或更新目录记录，并触发目录内文档解析、持久化和索引写入。</p>
     *
     * @param request 本地目录和递归配置
     * @return 导入统计和失败明细
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
     * 重建指定知识库目录的检索索引。
     *
     * <p>该操作基于已入库文档，不重新扫描磁盘目录。</p>
     *
     * @param id 知识库目录 ID
     * @return 重建结果统计
     */
    @PostMapping("/{id}/rebuild")
    public ApiResponse<KnowledgeFolderRebuildResponse> rebuildFolder(@PathVariable String id) {
        return ApiResponse.ok(knowledgeFolderService.rebuildFolder(id));
    }

    /**
     * 启用或停用知识库目录。
     *
     * <p>停用只影响检索可见性，不删除用户文件或 SQLite 中的解析结果。</p>
     *
     * @param id 知识库目录 ID
     * @param request 新的启用状态
     * @return 204 空响应
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
     * 删除知识库目录记录。
     *
     * <p>删除目录会解除文档归属并由服务层维护索引可见性，不直接删除本地原始文件。</p>
     *
     * @param id 知识库目录 ID
     * @return 204 空响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String id) {
        knowledgeFolderService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }
}
