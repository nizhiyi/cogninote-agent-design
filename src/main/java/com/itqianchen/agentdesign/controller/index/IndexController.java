package com.itqianchen.agentdesign.controller.index;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.dto.index.IndexStatusResponse;
import com.itqianchen.agentdesign.dto.index.RebuildIndexResponse;
import com.itqianchen.agentdesign.service.index.IndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexService indexService;

    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    @GetMapping("/status")
    public ApiResponse<IndexStatusResponse> status() {
        return ApiResponse.ok(indexService.status());
    }

    @PostMapping("/rebuild")
    public ApiResponse<RebuildIndexResponse> rebuild() {
        return ApiResponse.ok(indexService.rebuild());
    }
}


