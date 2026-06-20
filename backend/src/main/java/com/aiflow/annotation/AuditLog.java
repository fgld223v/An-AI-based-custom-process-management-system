package com.aiflow.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解。
 * 标记在 Controller 方法上，AOP 切面自动将操作记录写入 operation_log 表。
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作名称，如 "AI_GENERATE_PROCESS"、"AI_GENERATE_FORM" */
    String value();

    /** 操作目标类型，如 "template"、"instance"、"form"。默认空表示自动推断 */
    String targetType() default "";

    /** 是否记录请求参数内容到 operation_content 字段。默认 true */
    boolean recordParams() default true;

    /** 是否记录返回值摘要到 operation_content 字段。默认 true */
    boolean recordResult() default true;
}
