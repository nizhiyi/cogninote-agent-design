package com.itqianchen.agentdesign.controller.websearch;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchSettingsRequest;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchSettingsResponse;
import com.itqianchen.agentdesign.domain.dto.websearch.WebSearchTestResponse;
import com.itqianchen.agentdesign.service.websearch.WebSearchSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 联网搜索设置控制器。
 *
 * <p>控制器只暴露配置读写和连通性测试，API Key 明文只允许通过 PUT 请求进入服务端。</p>
 */
@RestController
@RequestMapping("/api/web-search")
public class WebSearchSettingsController {

    private final WebSearchSettingsService webSearchSettingsService;

    public WebSearchSettingsController(WebSearchSettingsService webSearchSettingsService) {
        this.webSearchSettingsService = webSearchSettingsService;
    }

    @GetMapping("/settings")
    public ApiResponse<WebSearchSettingsResponse> settings() {
        return ApiResponse.ok(webSearchSettingsService.settings());
    }

    @PutMapping("/settings")
    public ApiResponse<WebSearchSettingsResponse> update(@Valid @RequestBody WebSearchSettingsRequest request) {
        return ApiResponse.ok(webSearchSettingsService.update(request));
    }

    @PostMapping("/test")
    public ApiResponse<WebSearchTestResponse> test() {
        return ApiResponse.ok(webSearchSettingsService.test());
    }
}
