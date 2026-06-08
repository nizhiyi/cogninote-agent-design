package com.itqianchen.agentdesign.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * System Status 控制器 测试 承担 系统状态 模块的主要职责。
 * <p>注释说明维护边界，不改变现有运行逻辑。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.storage.base-dir=target/test-cogninote-data",
        "server.address=127.0.0.1"
})
class SystemStatusControllerTests {

    @Autowired
    private com.itqianchen.agentdesign.service.system.SystemStatusService systemStatusService;

    /**
     * 执行 系统状态 中的 status Returns Application Health And Storage Path 步骤。
     * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
     */
    @Test
    void statusReturnsApplicationHealthAndStoragePath() {
        com.itqianchen.agentdesign.dto.system.SystemStatusResponse response = systemStatusService.status();

        /**
         * 执行 系统状态 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.appName()).isEqualTo("CogniNote Agent");
        /**
         * 执行 系统状态 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.version()).isEqualTo("0.0.1-SNAPSHOT");
        /**
         * 执行 系统状态 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.status()).isEqualTo("UP");
        /**
         * 执行 系统状态 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.dataDir()).contains("target");
        /**
         * 执行 系统状态 中的 assert That 步骤。
         * <p>该方法是当前类型内部复用或对外暴露的明确业务边界。</p>
         */
        assertThat(response.desktopMode()).isFalse();
    }
}


