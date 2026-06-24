package com.itqianchen.agentdesign.controller.index;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.domain.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.service.index.IndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Index 控制器 暴露 检索索引 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexService indexService;

    /**
     * 注入索引服务。
     *
     * @param indexService 检索索引应用服务
     */
    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * 读取当前索引统计。
     *
     * @return 文档数、chunk 数和索引可用状态
     */
    @GetMapping("/status")
    public ApiResponse<IndexStatusResponse> status() {
        return ApiResponse.ok(indexService.status());
    }

    /**
     * 重建全部检索索引。
     *
     * <p>重建会读取当前文档快照并覆盖 Lucene 索引，调用方应避免在频繁导入时重复触发。</p>
     *
     * @return 重建结果统计
     */
    @PostMapping("/rebuild")
    public ApiResponse<RebuildIndexResponse> rebuild() {
        return ApiResponse.ok(indexService.rebuild());
    }
}


