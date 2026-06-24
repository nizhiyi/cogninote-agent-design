package com.itqianchen.agentdesign.controller.graph;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphEvidenceResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphRebuildRequest;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphRunResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphStatusResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphSummaryResponse;
import com.itqianchen.agentdesign.domain.dto.graph.KnowledgeGraphViewResponse;
import com.itqianchen.agentdesign.service.graph.KnowledgeGraphService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 知识图谱 HTTP 接口。
 */
@RestController
@RequestMapping("/api/knowledge-graphs")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    /**
     * 注入知识图谱应用服务。
     *
     * @param knowledgeGraphService 图谱构建、查询和事件订阅服务
     */
    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /**
     * 查询已生成图谱清单。
     *
     * <p>该接口只返回 scope 摘要，不携带视图 payload；前端点击条目后再读取具体视图。</p>
     *
     * @return 已生成图谱摘要列表
     */
    @GetMapping
    public ApiResponse<List<KnowledgeGraphSummaryResponse>> listGeneratedGraphs() {
        return ApiResponse.ok(knowledgeGraphService.listGeneratedGraphs());
    }

    /**
     * 删除指定范围已生成的知识图谱。
     *
     * <p>只删除图谱节点、关系、证据、视图和运行历史，不删除用户原始目录、文档或 chunk 抽取缓存。</p>
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全局范围可为空
     * @return 删除完成标记
     */
    @DeleteMapping
    public ApiResponse<Boolean> deleteGeneratedGraph(
            @RequestParam String scopeType,
            @RequestParam(required = false) String scopeId
    ) {
        knowledgeGraphService.deleteGeneratedGraph(scopeType, scopeId);
        return ApiResponse.ok(true);
    }

    /**
     * 启动指定范围的图谱重建任务。
     *
     * <p>该接口只创建运行记录并异步推进，前端需通过 run/status/events 追踪结果。</p>
     *
     * @param request 图谱范围配置
     * @return 新建运行记录
     */
    @PostMapping("/rebuild")
    public ApiResponse<KnowledgeGraphRunResponse> rebuild(@Valid @RequestBody KnowledgeGraphRebuildRequest request) {
        return ApiResponse.ok(knowledgeGraphService.rebuild(request.scopeType(), request.scopeId()));
    }

    /**
     * 查询指定范围的图谱状态。
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全局范围可为空
     * @return 当前运行状态和统计
     */
    @GetMapping("/status")
    public ApiResponse<KnowledgeGraphStatusResponse> status(
            @RequestParam String scopeType,
            @RequestParam(required = false) String scopeId
    ) {
        return ApiResponse.ok(knowledgeGraphService.status(scopeType, scopeId));
    }

    /**
     * 读取指定范围和视图类型的图谱数据。
     *
     * <p>payload 由服务层按前端视图模型生成，Controller 不解释节点和边结构。</p>
     *
     * @param scopeType 范围类型
     * @param scopeId 范围 ID；全局范围可为空
     * @param viewType 前端请求的视图类型
     * @return 图谱视图响应
     */
    @GetMapping("/view")
    public ApiResponse<KnowledgeGraphViewResponse> view(
            @RequestParam String scopeType,
            @RequestParam(required = false) String scopeId,
            @RequestParam String viewType
    ) {
        return ApiResponse.ok(knowledgeGraphService.view(scopeType, scopeId, viewType));
    }

    /**
     * 查询单次图谱运行记录。
     *
     * @param runId 运行 ID
     * @return 运行详情
     */
    @GetMapping("/runs/{runId}")
    public ApiResponse<KnowledgeGraphRunResponse> run(@PathVariable String runId) {
        return ApiResponse.ok(knowledgeGraphService.getRun(runId));
    }

    /**
     * 订阅图谱运行事件流。
     *
     * <p>SSE 用于展示长任务进度；连接生命周期由发布器和 Servlet 容器共同管理。</p>
     *
     * @param runId 运行 ID
     * @return SSE 事件发射器
     */
    @GetMapping(path = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String runId) {
        return knowledgeGraphService.subscribe(runId);
    }

    /**
     * 请求取消图谱运行。
     *
     * @param runId 运行 ID
     * @return 是否成功标记取消
     */
    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable String runId) {
        return ApiResponse.ok(knowledgeGraphService.cancel(runId));
    }

    /**
     * 查询节点证据。
     *
     * <p>证据用于解释图谱节点来源，不会重新触发抽取或合并。</p>
     *
     * @param nodeId 节点 ID
     * @return 节点关联证据
     */
    @GetMapping("/nodes/{nodeId}/evidence")
    public ApiResponse<List<KnowledgeGraphEvidenceResponse>> nodeEvidence(@PathVariable String nodeId) {
        return ApiResponse.ok(knowledgeGraphService.nodeEvidence(nodeId));
    }

    /**
     * 查询边证据。
     *
     * <p>证据用于解释关系来源，不会重新触发抽取或合并。</p>
     *
     * @param edgeId 边 ID
     * @return 边关联证据
     */
    @GetMapping("/edges/{edgeId}/evidence")
    public ApiResponse<List<KnowledgeGraphEvidenceResponse>> edgeEvidence(@PathVariable String edgeId) {
        return ApiResponse.ok(knowledgeGraphService.edgeEvidence(edgeId));
    }
}
