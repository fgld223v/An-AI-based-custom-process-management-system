package com.aiflow.common;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 * <p>
 * 使用 {@code @RestControllerAdvice} 拦截所有 Controller 抛出的异常，
 * 统一转换为 {@link ApiResponse} 格式返回，确保前端始终收到结构一致的错误响应。
 * </p>
 * <p>覆盖的异常类型包括：</p>
 * <ul>
 *   <li>{@link BusinessException} —— 自定义业务异常，动态设置 HTTP 状态码</li>
 *   <li>{@link IllegalArgumentException} / {@link IllegalStateException} —— 非法参数/状态</li>
 *   <li>{@link MethodArgumentNotValidException} —— @Valid 校验失败，提取首个字段错误</li>
 *   <li>{@link ConstraintViolationException} —— 方法级参数校验失败</li>
 *   <li>{@link AccessDeniedException} —— 权限不足（403）</li>
 *   <li>{@link AuthenticationException} —— 认证失败（401）</li>
 *   <li>{@link HttpMessageNotReadableException} —— 请求体解析失败</li>
 *   <li>{@link Exception} —— 兜底处理，返回 500</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常，HTTP 状态码由异常的 code 字段决定。
     *
     * @param e        业务异常
     * @param response HTTP 响应对象，用于动态设置状态码
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e, HttpServletResponse response) {
        // 动态设置 HTTP 状态码，优先使用异常中携带的 code
        response.setStatus(e.getCode() != null ? e.getCode() : 500);
        return ApiResponse.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理非法参数异常（如参数为 null 或格式不正确）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        return ApiResponse.fail(400, e.getMessage());
    }

    /**
     * 处理非法状态异常（如操作在错误的状态下被调用）。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalStateException(IllegalStateException e) {
        return ApiResponse.fail(400, e.getMessage());
    }

    /**
     * 处理 @Valid 注解触发的 Bean 校验失败，提取第一个字段的错误信息返回。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        // 从所有字段错误中取出第一条，拼接为 "'字段名' 错误原因" 的格式
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> "'" + error.getField() + "' " + error.getDefaultMessage())
                .orElse("参数校验失败");
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理方法级参数校验失败（如 @RequestParam 上加 @Min/@Max 等）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException e) {
        return ApiResponse.fail(400, e.getMessage());
    }

    /**
     * 处理 Spring Security 权限不足异常，返回 403。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
        return ApiResponse.fail(403, "无访问权限");
    }

    /**
     * 处理认证失败异常（如用户名密码错误），返回 401。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthenticationException(AuthenticationException e) {
        return ApiResponse.fail(401, "用户名或密码错误");
    }

    /**
     * 处理请求体不可读异常（如 JSON 格式错误、字段类型不匹配）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return ApiResponse.fail(400, "请求参数格式错误，请检查数据类型");
    }

    /**
     * 兜底异常处理：捕获所有未被上述 handler 处理的异常，返回 500。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        return ApiResponse.fail(500, "系统异常：" + e.getMessage());
    }
}
