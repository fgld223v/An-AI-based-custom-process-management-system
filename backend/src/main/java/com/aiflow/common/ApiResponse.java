package com.aiflow.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
/**
 * 统一 API 响应封装。
 * <p>
 * 所有 Controller 返回此类型，保证前端收到的 JSON 结构一致：
 * {@code { "code": 200, "message": "success", "data": {...} }}。
 * 提供 {@code success()} / {@code fail()} 静态工厂方法简化构建。
 * </p>
 *
 * @param <T> data 字段的泛型类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    /** HTTP 状态码或业务状态码，200 表示成功 */
    private Integer code;

    /** 提示信息，成功时通常为 "success"，失败时为错误描述 */
    private String message;

    /** 响应携带的业务数据，可为 null（如失败响应时） */
    private T data;

    /**
     * 构建成功响应（无数据体）。
     *
     * @param <T> 泛型类型
     * @return code=200, message="success" 的响应
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .build();
    }

    /**
     * 构建携带数据的成功响应。
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=200, message="success" 的响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 构建失败响应，默认状态码 500。
     *
     * @param message 错误描述
     * @param <T>     泛型类型
     * @return code=500 的失败响应
     */
    public static <T> ApiResponse<T> fail(String message) {
        return fail(500, message);
    }

    /**
     * 构建指定状态码的失败响应。
     *
     * @param code    错误码
     * @param message 错误描述
     * @param <T>     泛型类型
     * @return 携带错误码和消息的响应
     */
    public static <T> ApiResponse<T> fail(Integer code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
}
