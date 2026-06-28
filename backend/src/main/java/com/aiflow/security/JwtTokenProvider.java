package com.aiflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 令牌提供者：负责创建、解析和验证 JWT。
 * <p>
 * 使用 HMAC-SHA 算法签名，密钥从配置文件 {@link JwtProperties#getSecret()} 读取。
 * 令牌中存储用户名（subject）以及 userId、role、systemRole、departmentId 等自定义声明。
 * </p>
 */
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /** 从配置的 secret 字符串派生的 HMAC 签名密钥 */
    private final SecretKey secretKey;

    /**
     * 构造器：初始化签名密钥。
     *
     * @param jwtProperties JWT 配置属性
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // 将配置中的 secret 字符串转换为 HMAC-SHA 密钥
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为指定用户创建 JWT 令牌。
     * <p>
     * 令牌包含以下声明：
     * <ul>
     *   <li>sub —— 用户名</li>
     *   <li>userId —— 用户 ID</li>
     *   <li>role —— 业务角色</li>
     *   <li>systemRole —— 系统角色（如 super_admin、biz_admin）</li>
     *   <li>departmentId —— 部门 ID</li>
     *   <li>iat —— 签发时间</li>
     *   <li>exp —— 过期时间（当前时间 + 配置的过期分钟数）</li>
     * </ul>
     * </p>
     *
     * @param currentUser 当前登录用户信息
     * @return 签发的 JWT 字符串
     */
    public String createToken(CurrentUser currentUser) {
        Instant now = Instant.now();
        // 过期时间 = 当前时间 + 配置的分钟数
        Instant expiration = now.plus(jwtProperties.getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(currentUser.getUsername())
                .claim("userId", currentUser.getId())
                .claim("role", currentUser.getRole())
                .claim("systemRole", currentUser.getSystemRole())
                .claim("departmentId", currentUser.getDepartmentId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)        // HMAC 签名
                .compact();                  // 序列化为字符串
    }

    /**
     * 从 JWT 令牌中提取用户名（即 subject 字段）。
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * 验证 JWT 令牌是否合法（签名有效且未过期）。
     * <p>
     * 如果令牌被篡改或已过期，{@code getClaims} 会抛出异常，
     * 调用方应捕获该异常以判断令牌无效。
     * </p>
     *
     * @param token JWT 字符串
     * @return true 表示令牌有效
     */
    public boolean validateToken(String token) {
        // 解析过程即完成签名验证和过期检查，异常由调用方处理
        getClaims(token);
        return true;
    }

    /**
     * 解析 JWT 令牌并返回其中的声明体（Claims）。
     * <p>
     * 使用配置的 HMAC 密钥进行签名验证，若签名不匹配或令牌过期则抛出异常。
     * </p>
     *
     * @param token JWT 字符串
     * @return 令牌中的声明数据
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)           // 设置验证密钥
                .build()
                .parseSignedClaims(token)        // 解析并验证签名
                .getPayload();                   // 获取 payload 声明体
    }
}
