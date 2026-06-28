package com.aiflow.common;

import lombok.Getter;

/**
 * 自定义业务异常。
 * <p>
 * 继承自 {@link RuntimeException}，除了异常消息外还携带一个
 * HTTP 状态码（{@code code}），供 {@link GlobalExceptionHandler}
 * 统一拦截后构造 {@link ApiResponse} 返回给客户端。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * if (user == null) {
 *     throw new BusinessException(404, "用户不存在");
 * }
 * }</pre>
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** HTTP 状态码或业务错误码，默认 500 */
    private final Integer code;

    /**
     * 使用默认状态码 500 构造业务异常。
     *
     * @param message 错误描述
     */
    public BusinessException(String message) {
        this(500, message);
    }

    /**
     * 使用指定状态码构造业务异常。
     *
     * @param code    HTTP 状态码或自定义业务错误码
     * @param message 错误描述
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
