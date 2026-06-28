package com.aiflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient 配置类。
 * <p>
 * 创建并暴露一个预配置的 {@link WebClient} Bean，用于与 DeepSeek AI API 通信。
 * 该客户端自动携带 API 密钥（Bearer Token）、JSON 内容类型头，
 * 并设置了 2MB 的内存解码上限以防止 OOM。
 * </p>
 */
@Configuration
public class WebClientConfig {

    /**
     * 构建 DeepSeek 专用的 {@link WebClient} 实例。
     * <p>
     * 配置包括：
     * <ul>
     *   <li>基础 URL：从 {@link AiConfig#getBaseUrl()} 读取</li>
     *   <li>认证头：Bearer Token 方式携带 API Key</li>
     *   <li>内容类型：固定为 application/json</li>
     *   <li>内存上限：响应体解码最大 2MB</li>
     * </ul>
     * </p>
     *
     * @param aiConfig DeepSeek 配置属性
     * @return 预配置的 WebClient 实例
     */
    @Bean
    public WebClient deepseekWebClient(AiConfig aiConfig) {
        return WebClient.builder()
            .baseUrl(aiConfig.getBaseUrl())
            // 每个请求自动附带 Authorization 头
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.getApiKey())
            // 固定请求内容类型为 JSON
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            // 限制响应体解码内存，防止大响应导致 OOM
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }
}
