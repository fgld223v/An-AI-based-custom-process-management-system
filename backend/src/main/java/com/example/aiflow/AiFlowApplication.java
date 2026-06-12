package com.example.aiflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@MapperScan("com.example.aiflow.module")
@EntityScan("com.aiflow.model")
@EnableJpaRepositories("com.aiflow.repository")
@SpringBootApplication(scanBasePackages = {"com.example.aiflow", "com.aiflow"})
public class AiFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiFlowApplication.class, args);
    }
}
