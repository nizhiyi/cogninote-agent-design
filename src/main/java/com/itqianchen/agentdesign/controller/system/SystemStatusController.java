package com.itqianchen.agentdesign.controller.system;

import com.itqianchen.agentdesign.common.api.ApiResponse;
import com.itqianchen.agentdesign.dto.system.SystemStatusResponse;
import com.itqianchen.agentdesign.service.system.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System Status 控制器 暴露 系统状态 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    /**
     * 注入 SystemStatusController 运行所需的协作者。
     * <p>依赖由 Spring 或测试环境统一提供，构造器本身不做业务副作用。</p>
     */
    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    /**
     * 执行 系统状态 中的 status 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @GetMapping("/status")
    public ApiResponse<SystemStatusResponse> status() {
        return ApiResponse.ok(systemStatusService.status());
    }
}


