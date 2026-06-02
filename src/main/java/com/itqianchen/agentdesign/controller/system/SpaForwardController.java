package com.itqianchen.agentdesign.controller.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({"/chat", "/knowledge", "/model-config", "/settings"})
    public String forwardSpaRoutes() {
        // Vue Router 使用 history 模式，用户直接刷新页面时后端必须返回 SPA 入口。
        // 这里只转发明确的前端路由，避免误吞 /api 或静态资源请求。
        return "forward:/index.html";
    }
}
