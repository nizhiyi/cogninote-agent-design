package com.itqianchen.agentdesign.service.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

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
