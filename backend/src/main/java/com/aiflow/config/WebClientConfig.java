package com.aiflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient deepseekWebClient(AiConfig aiConfig) {
        return WebClient.builder()
            .baseUrl(aiConfig.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiConfig.getApiKey())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
    }
}
