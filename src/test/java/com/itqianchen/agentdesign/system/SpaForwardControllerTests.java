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
 * Spa Forward 控制器 测试 承担 系统状态 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@WebMvcTest(SpaForwardController.class)
class SpaForwardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 执行 系统状态 中的 forwards Known Spa Routes To Index Html 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void forwardsKnownSpaRoutesToIndexHtml() throws Exception {
        /**
         * 执行 系统状态 中的 assert Spa Forward 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertSpaForward("/chat");
        /**
         * 执行 系统状态 中的 assert Spa Forward 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertSpaForward("/knowledge");
        /**
         * 执行 系统状态 中的 assert Spa Forward 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertSpaForward("/model-config");
        /**
         * 执行 系统状态 中的 assert Spa Forward 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertSpaForward("/settings");
    }

    /**
     * 执行 系统状态 中的 assert Spa Forward 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    private void assertSpaForward(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }
}
