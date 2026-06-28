package com.aiflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 * <p>
 * 继承 {@link OncePerRequestFilter} 确保每个请求仅执行一次。
 * 在每次请求中从 Authorization 头或 WebSocket 参数中提取 JWT 令牌，
 * 验证后将用户信息写入 {@link SecurityContextHolder}，后续的授权决策
 * 和业务代码可通过 {@link SecurityUtils} 直接获取当前用户。
 * </p>
 * <p>
 * 当认证失败时（令牌无效/过期），不中断请求链，仅清空上下文，
 * 由后续的 Spring Security 授权规则决定是否拒绝访问。
 * </p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)  // 低优先级，确保在其他过滤器之后执行
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    /**
     * 过滤器的核心逻辑：提取令牌 -> 验证 -> 加载用户 -> 写入安全上下文。
     * <p>
     * 仅在以下条件同时满足时才执行认证：
     * <ul>
     *   <li>请求中能提取到有效的 JWT 字符串</li>
     *   <li>当前 SecurityContext 中尚无认证信息（避免重复认证）</li>
     * </ul>
     * </p>
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 步骤 1：从请求中提取 JWT
        String token = resolveToken(request);

        // 步骤 2：若令牌存在且尚未认证，执行验证
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 验证令牌签名和有效期（无效会抛异常）
                jwtTokenProvider.validateToken(token);
                // 从令牌中提取用户名
                String username = jwtTokenProvider.getUsername(token);
                // 加载用户的详细信息（含权限列表）
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 构建认证令牌对象
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                // 附加请求详情（IP、sessionId 等）
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 步骤 3：将认证信息写入 SecurityContext
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);

                // 同时写入 Request Attribute，供 SecurityContextHolderFilter 等组件读取
                request.setAttribute(
                    org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context);
            } catch (Exception ignored) {
                // 令牌验证失败，清空上下文以保持干净的未认证状态
                SecurityContextHolder.clearContext();
            }
        }

        // 无论如何都继续执行过滤器链
        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求中解析 JWT 令牌。
     * <p>支持两种来源：</p>
     * <ul>
     *   <li>Authorization 头：{@code Bearer <token>} 格式（标准 REST 请求）</li>
     *   <li>查询参数 {@code access_token}：WebSocket 连接场景（因浏览器 WebSocket API 不支持自定义头）</li>
     * </ul>
     *
     * @param request HTTP 请求
     * @return 解析出的 JWT 字符串，若无法提取则返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        // --- 方式 1：从 Authorization 请求头提取 ---
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // "Bearer " 长度为 7，截取之后的 token 部分
            return bearerToken.substring(7);
        }

        // --- 方式 2：WebSocket 端点从查询参数中提取 ---
        // 浏览器 WebSocket 不支持自定义请求头，因此通过 URL 参数传递令牌
        if ("/ws/notifications".equals(request.getRequestURI())) {
            return request.getParameter("access_token");
        }

        return null;
    }
}
