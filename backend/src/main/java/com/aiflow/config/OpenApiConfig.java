package com.aiflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>访问地址：<a href="http://localhost:8080/swagger-ui/index.html">Swagger UI</a></p>
 * <p>API 规范 JSON：<a href="http://localhost:8080/v3/api-docs">OpenAPI 3.0 JSON</a></p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Flow — 基于 AI 的自定义流程管理系统 API")
                        .description("""
                                ## 模块概览
                                - **认证授权** — JWT 登录、角色权限
                                - **AI 智能流程生成** — 自然语言 → BPMN 2.0 XML
                                - **表单设计与管理** — 自定义字段、动态表单渲染
                                - **流程模板管理** — 模板 CRUD、发布/撤回、复制、市场
                                - **可视流程设计器** — bpmn-js 建模、会签/或签/抄送
                                - **流程实例** — 发起、提交、流转、驳回、催办
                                - **任务管理** — 待办/已办、审批/驳回、多实例进度
                                - **通知中心** — 站内信、超时提醒、催办通知、WebSocket 推送
                                - **统计分析** — 概览、趋势、办结率
                                - **自动化规则** — 条件自动审批、超时自动处理
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("AI Flow Team")
                                .email("dev@aiflow.local"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://aiflow.local")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发环境")
                ));
    }
}
