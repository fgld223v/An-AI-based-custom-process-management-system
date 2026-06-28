package com.aiflow.security;

import com.aiflow.model.AiServiceAccount;
import com.aiflow.repository.AiServiceAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务认证过滤器，通过 X-API-Key 请求头识别 AI 服务账号并注入 ROLE_AI_SERVICE 权限。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AiServiceAuthFilter extends OncePerRequestFilter implements Ordered {

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }

    private final AiServiceAccountRepository aiServiceAccountRepository;
    private final ObjectMapper objectMapper;

    public AiServiceAuthFilter(AiServiceAccountRepository aiServiceAccountRepository,
                               ObjectMapper objectMapper) {
        this.aiServiceAccountRepository = aiServiceAccountRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");

        if (!StringUtils.hasText(apiKey)) {
            // 没有 API Key → 放行给 JwtAuthenticationFilter 做普通用户认证
            filterChain.doFilter(request, response);
            return;
        }

        AiServiceAccount account = aiServiceAccountRepository
                .findByApiKey(apiKey)
                .orElse(null);

        if (account == null) {
            sendError(response, HttpStatus.FORBIDDEN.value(),
                    "API Key 无效");
            return;
        }

        if (!"active".equals(account.getStatus())) {
            sendError(response, HttpStatus.FORBIDDEN.value(),
                    "API Key 已被禁用");
            return;
        }

        // Authentication successful — set API service account as authenticated principal
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                account.getAccountName(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_AI_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
