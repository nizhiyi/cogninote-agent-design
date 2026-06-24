package com.itqianchen.agentdesign.controller.knowledge;


import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunOperation;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunStatus;
import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunOperation;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunScopeType;
import com.itqianchen.agentdesign.domain.enums.knowledge.KnowledgeFolderRunStatus;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderHealthResponse;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderRunBatchDeleteRequest;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderRunDeleteResponse;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderRunDetailResponse;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderRunPageResponse;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeFolderRunResponse;
import com.itqianchen.agentdesign.domain.dto.knowledge.KnowledgeHealthResponse;
import com.itqianchen.agentdesign.service.knowledge.KnowledgeHealthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 分页查询知识库维护运行记录。
     *
     * @param scopeType 范围类型；为空时查询全部
     * @param scopeId 范围 ID；全库范围为空
     * @param operations 操作类型列表；为空时不限制
     * @param statuses 状态列表；为空时不限制
     * @param keyword 模糊关键词，匹配任务 ID、目录名、路径和错误信息
     * @param timeFrom 起始时间戳；为空时不限制
     * @param timeTo 结束时间戳；为空时不限制
     * @param page 页码，从 1 开始
     * @param pageSize 每页数量
     * @return 分页运行记录
     */
    @GetMapping("/runs/page")
    public ApiResponse<KnowledgeFolderRunPageResponse> runsPage(
            @RequestParam(required = false) KnowledgeFolderRunScopeType scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam(required = false) List<KnowledgeFolderRunOperation> operations,
            @RequestParam(required = false) List<KnowledgeFolderRunStatus> statuses,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long timeFrom,
            @RequestParam(required = false) Long timeTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.ok(healthService.runsPage(
                scopeType,
                scopeId,
                operations,
                statuses,
                keyword,
                timeFrom,
                timeTo,
                page,
                pageSize
        ));
    }

    /**
     * 查询单条维护记录详情。
     *
     * @param runId 运行记录 ID
     * @return 维护记录详情
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<KnowledgeFolderRunDetailResponse> runDetail(@PathVariable String runId) {
        return ApiResponse.ok(healthService.runDetail(runId));
    }

    /**
     * 删除一条终态维护历史记录。
     *
     * @param runId 运行记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/runs/{runId}")
    public ApiResponse<KnowledgeFolderRunDeleteResponse> deleteRun(@PathVariable String runId) {
        return ApiResponse.ok(healthService.deleteRun(runId));
    }

    /**
     * 批量删除终态维护历史记录。
     *
     * @param request 批量删除请求
     * @return 删除结果
     */
    @PostMapping("/runs/batch-delete")
    public ApiResponse<KnowledgeFolderRunDeleteResponse> deleteRuns(
            @Valid @RequestBody KnowledgeFolderRunBatchDeleteRequest request
    ) {
        return ApiResponse.ok(healthService.deleteRuns(request.ids()));
    }
}
