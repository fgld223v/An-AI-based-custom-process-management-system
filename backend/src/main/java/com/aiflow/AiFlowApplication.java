package com.aiflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.aiflow.mapper")
@EntityScan("com.aiflow.model")
@EnableJpaRepositories("com.aiflow.repository")
@EnableScheduling
@SpringBootApplication
public class AiFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiFlowApplication.class, args);
    }
}
