package com.itqianchen.agentdesign.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itqianchen.agentdesign.controller.system.SpaForwardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 覆盖后端 SPA 路由转发规则。
 *
 * <p>这些路径由前端 router 接管，后端必须转发到 index.html，避免桌面或刷新页面时返回 404。</p>
 */
@WebMvcTest(SpaForwardController.class)
class SpaForwardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void forwardsKnownSpaRoutesToIndexHtml() throws Exception {
        assertSpaForward("/chat");
        assertSpaForward("/knowledge");
        assertSpaForward("/model-config");
        assertSpaForward("/settings");
    }

    private void assertSpaForward(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
