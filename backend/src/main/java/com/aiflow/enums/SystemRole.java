package com.aiflow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 系统角色枚举
 * 定义平台级别的用户角色，用于权限控制和功能访问隔离。
 */
@Getter
@AllArgsConstructor
public enum SystemRole implements DatabaseEnum {

    /** 超级管理员 -- 拥有系统全部权限 */
    SUPER_ADMIN("super_admin"),

    /** 业务管理员 -- 管理特定业务域的用户、模板和流程 */
    BIZ_ADMIN("biz_admin"),

    /** 普通用户 -- 基本权限，可发起和审批流程 */
    NORMAL_USER("normal_user");

    @JsonValue
    private final String value;
}
