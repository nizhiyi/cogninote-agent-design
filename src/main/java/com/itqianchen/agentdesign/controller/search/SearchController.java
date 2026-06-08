package com.itqianchen.agentdesign.controller.search;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.dto.search.SearchRequest;
import com.itqianchen.agentdesign.dto.search.SearchResponse;
import com.itqianchen.agentdesign.service.search.SearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Search 控制器 暴露 检索索引 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    /**
     * 注入 SearchController 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 执行 检索索引 中的 search 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @PostMapping
    public ApiResponse<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        return ApiResponse.ok(searchService.search(request));
    }
}


