package com.itqianchen.agentdesign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CogninoteAgentDesignApplication {

    public static void main(String[] args) {
        SpringApplication.run(CogninoteAgentDesignApplication.class, args);
    }

}


