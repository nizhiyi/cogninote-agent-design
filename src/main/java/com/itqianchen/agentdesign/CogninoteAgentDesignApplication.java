package com.itqianchen.agentdesign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CogninoteAgentDesignApplication 是应用启动入口。
 * <p>负责交给 Spring Boot 完成组件扫描、配置绑定和运行时初始化。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CogninoteAgentDesignApplication {

    /**
     * 启动 Spring Boot 应用。
     * <p>命令行参数会原样交给 {@code SpringApplication.run}。</p>
     */
    public static void main(String[] args) {
        SpringApplication.run(CogninoteAgentDesignApplication.class, args);
    }

}


