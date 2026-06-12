package com.itqianchen.agentdesign.service.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/**
 * 覆盖后端版本号解析顺序。
 *
 * <p>部署显式配置优先于 build-info，缺失时才回落到 dev，避免状态页展示误导性的 SNAPSHOT。</p>
 */
class SystemStatusServiceTests {

    @Test
    void resolveVersionPrefersConfiguredVersion() {
        assertThat(SystemStatusService.resolveVersion(" 1.2.3-override ", buildProperties("9.9.9")))
                .isEqualTo("1.2.3-override");
    }

    @Test
    void resolveVersionUsesBuildInfoVersionWhenNoOverrideExists() {
        assertThat(SystemStatusService.resolveVersion("", buildProperties("0.1.32")))
                .isEqualTo("0.1.32");
    }

    @Test
    void resolveVersionFallsBackToDevWhenBuildInfoIsMissing() {
        assertThat(SystemStatusService.resolveVersion("", null)).isEqualTo("dev");
    }

    private BuildProperties buildProperties(String version) {
        Properties entries = new Properties();
        entries.setProperty("version", version);
        return new BuildProperties(entries);
    }
}
