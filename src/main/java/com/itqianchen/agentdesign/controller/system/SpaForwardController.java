package com.itqianchen.agentdesign.controller.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Spa Forward 控制器 暴露 系统状态 的 HTTP 接口。
 * <p>控制器只负责请求参数、响应包装和服务层委派，避免承载业务细节。</p>
 */
@Controller
public class SpaForwardController {

    /**
     * 将明确的前端路由转发给 SPA 入口。
     *
     * <p>只匹配已知页面，避免拦截 API、静态资源或后续新增的后端接口。</p>
     *
     * @return SPA 入口转发地址
     */
    @GetMapping({"/chat", "/knowledge", "/model-config", "/settings"})
    public String forwardSpaRoutes() {
        // Vue Router 使用 history 模式，用户直接刷新页面时后端必须返回 SPA 入口。
        // 这里只转发明确的前端路由，避免误吞 /api 或静态资源请求。
        return "forward:/index.html";
    }
}
