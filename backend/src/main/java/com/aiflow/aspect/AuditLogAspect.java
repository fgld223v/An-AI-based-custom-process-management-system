package com.aiflow.aspect;

import com.aiflow.annotation.AuditLog;
import com.aiflow.model.OperationLog;
import com.aiflow.repository.OperationLogRepository;
import com.aiflow.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 审计日志切面 — 项目第二个 AOP 切面。
 * 拦截所有 @AuditLog 标记的方法，自动将操作记录写入 operation_log 表。
 *
 * <p>参考 DataMaskAspect 的结构，遵循项目统一的 AOP 模式。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final OperationLogRepository operationLogRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        OperationLog opLog = new OperationLog();
        opLog.setOperationType(auditLog.value());
        opLog.setCreatedAt(LocalDateTime.now());

        // 操作人：优先从 JWT 获取，无则记 "system"
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            opLog.setOperatorId(userId);
        } catch (Exception e) {
            // 无认证用户（如 AI 服务账号），operatorId 保持 null
        }

        // 从 HTTP 请求中提取 IP 和 User-Agent
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                opLog.setRequestIp(getClientIp(request));
                opLog.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // 非 Web 上下文，忽略
        }

        // 记录入参摘要
        if (auditLog.recordParams()) {
            try {
                Object[] args = pjp.getArgs();
                if (args != null && args.length > 0) {
                    // 跳过 HttpServletRequest/Response 等非业务参数
                    Object[] filteredArgs = new Object[args.length];
                    int count = 0;
                    for (Object arg : args) {
                        if (arg instanceof jakarta.servlet.http.HttpServletRequest
                                || arg instanceof jakarta.servlet.http.HttpServletResponse) {
                            continue;
                        }
                        filteredArgs[count++] = arg;
                    }
                    if (count > 0) {
                        Object[] finalArgs = new Object[count];
                        System.arraycopy(filteredArgs, 0, finalArgs, 0, count);
                        String paramsJson = objectMapper.writeValueAsString(finalArgs);
                        // 限制入参内容长度，防止大文本撑爆字段
                        if (paramsJson.length() > 2000) {
                            paramsJson = paramsJson.substring(0, 2000) + "...(truncated)";
                        }
                        opLog.setOperationContent(paramsJson);
                    }
                }
            } catch (JsonProcessingException e) {
                log.debug("序列化入参失败: {}", e.getMessage());
            }
        }

        // 执行目标方法
        Object result;
        long startTime = System.currentTimeMillis();
        try {
            result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - startTime;

            // 记录成功 + 追加耗时信息
            if (auditLog.recordResult()) {
                String existing = opLog.getOperationContent() != null
                        ? opLog.getOperationContent() + " | "
                        : "";
                opLog.setOperationContent(existing + "OK (" + elapsed + "ms)");
            }
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - startTime;
            // 记录失败
            String errorMsg = Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
            String existing = opLog.getOperationContent() != null
                    ? opLog.getOperationContent() + " | "
                    : "";
            opLog.setOperationContent(existing + "ERROR: " + errorMsg + " (" + elapsed + "ms)");
            // 失败时也落库
            saveLog(opLog);
            throw e;
        }

        saveLog(opLog);
        return result;
    }

    private void saveLog(OperationLog opLog) {
        try {
            operationLogRepository.save(opLog);
        } catch (Exception e) {
            log.error("审计日志写入失败 (操作类型: {}): {}", opLog.getOperationType(), e.getMessage());
        }
    }

    /**
     * 获取客户端真实 IP，考虑反向代理。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
