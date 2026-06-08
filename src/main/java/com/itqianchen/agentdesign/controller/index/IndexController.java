package com.itqianchen.agentdesign.controller.index;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
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
     * 注入 IndexController 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * 执行 检索索引 中的 status 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @GetMapping("/status")
    public ApiResponse<IndexStatusResponse> status() {
        return ApiResponse.ok(indexService.status());
    }

    /**
     * 执行 检索索引 中的 rebuild 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @PostMapping("/rebuild")
    public ApiResponse<RebuildIndexResponse> rebuild() {
        return ApiResponse.ok(indexService.rebuild());
    }
}


