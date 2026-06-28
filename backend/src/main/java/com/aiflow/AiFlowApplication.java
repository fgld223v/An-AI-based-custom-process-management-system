package com.aiflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AiFlow 平台 Spring Boot 启动类。
 * <p>
 * 集成了 MyBatis（Mapper 扫描）、JPA（实体扫描与 Repository 自动注册）、
 * 以及定时任务调度支持。作为整个后端服务的入口，负责初始化 Spring 容器并启动嵌入式 Web 服务器。
 * </p>
 */
@MapperScan("com.aiflow.mapper")            // 扫描 MyBatis Mapper 接口
@EntityScan("com.aiflow.model")              // 扫描 JPA 实体类
@EnableJpaRepositories("com.aiflow.repository") // 启用 JPA Repository 自动代理
@EnableScheduling                            // 启用 @Scheduled 定时任务支持
@SpringBootApplication
public class AiFlowApplication {

    /**
     * 应用主入口：启动 Spring Boot 应用并加载全部自动配置。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiFlowApplication.class, args);
    }
}
