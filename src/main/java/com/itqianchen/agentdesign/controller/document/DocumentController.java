package com.itqianchen.agentdesign.controller.document;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.common.api.ResourceNotFoundException;
import com.itqianchen.agentdesign.domain.dto.document.DocumentChunkResponse;
import com.itqianchen.agentdesign.domain.dto.document.DocumentSummaryResponse;
import com.itqianchen.agentdesign.domain.dto.document.IngestDocumentsRequest;
import com.itqianchen.agentdesign.domain.dto.document.IngestDocumentsResponse;
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

/**
 * Document 控制器 暴露 文档管理 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final DocumentManagementService documentManagementService;
    private final DocumentQueryService documentQueryService;

    /**
     * 注入文档导入、管理和查询服务。
     *
     * @param ingestionService 文档导入编排服务
     * @param documentManagementService 文档生命周期管理服务
     * @param documentQueryService 文档读取服务
     */
    public DocumentController(
            DocumentIngestionService ingestionService,
            DocumentManagementService documentManagementService,
            DocumentQueryService documentQueryService
    ) {
        this.ingestionService = ingestionService;
        this.documentManagementService = documentManagementService;
        this.documentQueryService = documentQueryService;
    }

    /**
     * 返回文档摘要列表，不展开 chunk 正文。
     *
     * @return 文档摘要列表
     */
    @GetMapping
    public ApiResponse<List<DocumentSummaryResponse>> listDocuments() {
        return ApiResponse.ok(documentQueryService.listDocuments());
    }

    /**
     * 查询 文档片段详情。
     * <p>该接口供来源预览弹窗读取完整片段内容，不改变聊天消息中的来源快照。</p>
     *
     * @param chunkId 文档片段 ID
     * @return 片段详情
     */
    @GetMapping("/chunks/{chunkId}")
    public ApiResponse<DocumentChunkResponse> getChunk(@PathVariable String chunkId) {
        return ApiResponse.ok(documentQueryService.getChunk(chunkId));
    }

    /**
     * 导入指定目录中的文档。
     *
     * <p>请求只携带本地路径和递归策略，具体文件过滤、解析、切块和索引写入由服务层串联。</p>
     *
     * @param request 导入目录和递归配置
     * @return 导入统计和失败明细
     */
    @PostMapping("/ingest")
    public ApiResponse<IngestDocumentsResponse> ingest(@Valid @RequestBody IngestDocumentsRequest request) {
        return ApiResponse.ok(ingestionService.ingestFolder(request.folderPath(), request.recursiveOrDefault()));
    }

    /**
     * 删除单个文档及其索引。
     *
     * <p>文档不存在时转为 404，避免前端把未删除任何记录误判为成功。</p>
     *
     * @param id 文档 ID
     * @return 204 空响应
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        if (!documentManagementService.deleteDocument(id)) {
            throw new ResourceNotFoundException("Document not found: " + id);
        }
        return ResponseEntity.noContent().build();
    }
}


