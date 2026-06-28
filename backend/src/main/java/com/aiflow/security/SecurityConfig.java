package com.aiflow.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 核心配置类。
 * <p>
 * 负责定义整个应用的安全策略，包括：
 * <ul>
 *   <li>禁用 CSRF（前后端分离，基于 JWT 的无状态认证）</li>
 *   <li>配置 CORS 跨域规则（允许本地前端开发服务器访问）</li>
 *   <li>设置无会话策略（STATELESS），每个请求独立认证</li>
 *   <li>定义基于角色（SUPER_ADMIN / BIZ_ADMIN）的 URL 权限规则</li>
 *   <li>注册 JWT 认证过滤器与 AI 服务认证过滤器</li>
 *   <li>提供 BCrypt 密码编码器和认证管理器 Bean</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AiServiceAuthFilter aiServiceAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                         AiServiceAuthFilter aiServiceAuthFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.aiServiceAuthFilter = aiServiceAuthFilter;
    }

    /**
     * 构建安全过滤器链——Spring Security 的核心配置入口。
     * <p>
     * 权限规则分层（由上到下匹配，命中即停止）：
     * <ol>
     *   <li>OPTIONS 预检请求全部放行</li>
     *   <li>登录/注册/重置密码等认证端点放行</li>
     *   <li>健康检查、错误页面、Swagger 文档放行</li>
     *   <li>WebSocket 通知端点要求已认证</li>
     *   <li>管理后台（/api/admin/**）仅限 SUPER_ADMIN</li>
     *   <li>流程管理（/api/my-processes/**）仅限 BIZ_ADMIN</li>
     *   <li>业务监控（/api/business-monitor/**）允许 BIZ_ADMIN 或 SUPER_ADMIN</li>
     *   <li>运行时监控（/api/runtime-monitor/**）仅限 SUPER_ADMIN</li>
     *   <li>表单 CRUD 允许 BIZ_ADMIN 或 SUPER_ADMIN</li>
     *   <li>流程模板管理仅限 SUPER_ADMIN</li>
     *   <li>模板市场发布/撤回仅限 SUPER_ADMIN，复制允许 BIZ_ADMIN</li>
     *   <li>其余 /api/** 接口要求已认证</li>
     *   <li>其他请求全部放行</li>
     * </ol>
     * 过滤器执行顺序：AiServiceAuthFilter -> JwtAuthenticationFilter -> UsernamePasswordAuthenticationFilter
     * </p>
     *
     * @param http HttpSecurity 配置构建器
     * @return 构建完成的过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT 无状态认证，无需 CSRF 保护
                .csrf(AbstractHttpConfigurer::disable)
                // 启用自定义 CORS 配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 不创建 HTTP Session，每个请求独立携带 JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // URL 权限规则配置
                .authorizeHttpRequests(auth -> auth
                        // --- 公开端点 ---
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register", "/api/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // --- WebSocket ---
                        .requestMatchers("/ws/notifications").authenticated()

                        // --- 超级管理员专属 ---
                        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/runtime-monitor/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/process-templates", "/api/process-templates/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/process-templates/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/process-templates/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/template-market/publish-template", "/api/template-market/*/withdraw").hasRole("SUPER_ADMIN")

                        // --- 业务管理员专属 ---
                        .requestMatchers("/api/my-processes/**").hasRole("BIZ_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/template-market/*/copy").hasRole("BIZ_ADMIN")

                        // --- 业务管理员或超级管理员 ---
                        .requestMatchers("/api/business-monitor/**").hasAnyRole("BIZ_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/forms", "/api/forms/published").hasAnyRole("BIZ_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/forms", "/api/forms/*/publish", "/api/forms/*/disable").hasAnyRole("BIZ_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/forms/**").hasAnyRole("BIZ_ADMIN", "SUPER_ADMIN")

                        // --- 其余 API 需认证 ---
                        .requestMatchers("/api/**").authenticated()

                        // --- 兜底放行 ---
                        .anyRequest().permitAll()
                )
                // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // AI 服务认证过滤器在最外层，优先级最高
                .addFilterBefore(aiServiceAuthFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 暴露 AuthenticationManager Bean，供登录等场景手动调用认证。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 密码编码器：使用 BCrypt 单向哈希，自动加盐。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 跨域配置源。
     * <p>
     * 允许本地开发服务器（Vite 默认端口 5173/5174）跨域访问所有接口，
     * 支持常用 HTTP 方法，允许任意请求头，不携带凭据（cookie）。
     * </p>
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 仅允许前端开发服务器的来源
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173", "http://localhost:5174",
                "http://127.0.0.1:5173", "http://127.0.0.1:5174"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // JWT 认证不使用 cookie，无需 allowCredentials
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
