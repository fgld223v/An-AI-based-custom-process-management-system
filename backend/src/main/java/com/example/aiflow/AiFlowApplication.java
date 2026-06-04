package com.example.aiflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.example.aiflow.module")
@SpringBootApplication
public class AiFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiFlowApplication.class, args);
    }
}
