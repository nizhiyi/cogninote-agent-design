package com.itqianchen.agentdesign.controller.search;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.dto.search.SearchRequest;
import com.itqianchen.agentdesign.domain.dto.search.SearchResponse;
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
     * 注入检索服务。
     *
     * @param searchService 检索编排服务
     */
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 执行知识库检索。
     *
     * <p>请求中的模式和 topK 会由服务层归一化，Controller 保持前端参数原样传入。</p>
     *
     * @param request 检索请求
     * @return 命中的文档片段
     */
    @PostMapping
    public ApiResponse<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        return ApiResponse.ok(searchService.search(request));
    }
}


