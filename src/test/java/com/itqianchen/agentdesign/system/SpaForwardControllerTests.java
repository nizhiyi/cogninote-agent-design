package com.itqianchen.agentdesign.system;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.itqianchen.agentdesign.controller.system.SpaForwardController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

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
