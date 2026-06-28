package com.aiflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI 大模型配置属性类。
 * <p>
 * 自动绑定 application.yml 中以 {@code ai.deepseek} 为前缀的配置项，
 * 包括 API 密钥、请求地址、模型名称、超时时间以及上下文消息上限。
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "ai.deepseek")
public class AiConfig {

    /** DeepSeek API 访问密钥 */
    private String apiKey;

    /** DeepSeek API 基础 URL，默认指向官方 v1 接口 */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 使用的模型名称，默认为 deepseek-chat */
    private String model = "deepseek-chat";

    /** HTTP 请求超时时间（秒），默认 60 秒 */
    private int timeoutSeconds = 60;

    /** 对话上下文中保留的最大消息条数，默认 20 条 */
    private int maxContextMessages = 20;

    // ==================== Getter / Setter ====================

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public int getMaxContextMessages() { return maxContextMessages; }
    public void setMaxContextMessages(int maxContextMessages) { this.maxContextMessages = maxContextMessages; }
}
