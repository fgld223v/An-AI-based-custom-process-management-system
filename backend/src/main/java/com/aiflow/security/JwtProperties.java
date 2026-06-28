package com.aiflow.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性类。
 * <p>
 * 自动绑定 application.yml 中以 {@code jwt} 为前缀的配置项，
 * 包括签名密钥（secret）和令牌过期时间（expirationMinutes）。
 * 通过 {@code @EnableConfigurationProperties(JwtProperties.class)} 激活。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** JWT 签名密钥（HMAC-SHA 算法使用），需保证足够长度和复杂度 */
    private String secret;

    /** JWT 令牌过期时间（分钟），超时后令牌自动失效 */
    private Long expirationMinutes;
}
