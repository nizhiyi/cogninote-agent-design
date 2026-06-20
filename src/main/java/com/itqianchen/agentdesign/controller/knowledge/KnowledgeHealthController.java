package com.itqianchen.agentdesign.controller.knowledge;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderHealthResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeFolderRunResponse;
import com.itqianchen.agentdesign.dto.knowledge.KnowledgeHealthResponse;
import com.itqianchen.agentdesign.service.knowledge.KnowledgeHealthService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库健康诊断接口。
 *
 * <p>接口只读取当前 SQLite、Lucene 状态和本地文件元数据，不会触发同步、重建或删除。</p>
 */
@RestController
@RequestMapping("/api/knowledge-health")
public class KnowledgeHealthController {

    private final KnowledgeHealthService healthService;

    /**
     * 注入知识库健康诊断服务。
     *
     * @param healthService 健康诊断服务
     */
    public KnowledgeHealthController(KnowledgeHealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * 读取全库健康快照。
     *
     * @return 全库健康响应
     */
    @GetMapping
    public ApiResponse<KnowledgeHealthResponse> health() {
        return ApiResponse.ok(healthService.health());
    }

    /**
     * 读取单个目录的健康详情。
     *
     * @param id 知识库目录 ID
     * @return 目录健康响应
     */
    @GetMapping("/folders/{id}")
    public ApiResponse<KnowledgeFolderHealthResponse> folderHealth(@PathVariable String id) {
        return ApiResponse.ok(healthService.folderHealth(id));
    }

    /**
     * 查询知识库维护运行记录。
     *
     * @param scopeType 范围类型；为空时查询全部
     * @param scopeId 范围 ID；全库范围为空
     * @param limit 最大返回数量
     * @return 运行记录列表
     */
    @GetMapping("/runs")
    public ApiResponse<List<KnowledgeFolderRunResponse>> runs(
            @RequestParam(required = false) KnowledgeFolderRunScopeType scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(healthService.runs(scopeType, scopeId, limit));
    }
}
