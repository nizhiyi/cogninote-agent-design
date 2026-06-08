package com.itqianchen.agentdesign.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 测试 My Batis 配置 集中维护 测试支撑 相关的 Spring 配置。
 * <p>这里的 Bean 或扫描配置会影响应用启动阶段的基础设施装配。</p>
 */
@Configuration
@MapperScan("com.itqianchen.agentdesign.mapper.test")
public class TestMyBatisConfig {
}
