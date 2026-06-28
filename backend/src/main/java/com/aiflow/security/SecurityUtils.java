package com.aiflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类：提供静态方法便捷获取当前登录用户信息。
 * <p>
 * 从 {@link SecurityContextHolder} 中读取由 {@link JwtAuthenticationFilter}
 * 写入的认证信息，并提取 {@link CurrentUser} 主体，供业务层随时调用。
 * </p>
 * <p>
 * 所有方法均为静态方法，工具类禁止实例化。
 * 每个查询方法都提供两种命名风格（如 {@code currentUser()} 和 {@code getCurrentUser()}），
 * 以适应不同编码习惯。
 * </p>
 */
public final class SecurityUtils {

    /** 私有构造器，防止工具类被实例化 */
    private SecurityUtils() {
    }

    /**
     * 从安全上下文中获取当前登录的 {@link CurrentUser}。
     * <p>
     * 如果未认证（匿名用户）或 principal 不是 {@link CurrentUser} 类型，返回 null。
     * </p>
     *
     * @return 当前用户信息，未登录则返回 null
     */
    public static CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 未认证或 principal 类型不匹配时返回 null
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }
        return currentUser;
    }

    /**
     * {@link #currentUser()} 的别名方法。
     */
    public static CurrentUser getCurrentUser() {
        return currentUser();
    }

    /**
     * 获取当前登录用户的 ID。
     *
     * @return 用户 ID，未登录则返回 null
     */
    public static Long currentUserId() {
        CurrentUser currentUser = currentUser();
        return currentUser == null ? null : currentUser.getId();
    }

    /**
     * {@link #currentUserId()} 的别名方法。
     */
    public static Long getCurrentUserId() {
        return currentUserId();
    }

    /**
     * 获取当前登录用户的系统角色。
     *
     * @return 系统角色字符串（如 "super_admin"、"biz_admin"），未登录则返回 null
     */
    public static String currentUserSystemRole() {
        CurrentUser currentUser = currentUser();
        return currentUser == null ? null : currentUser.getSystemRole();
    }

    /**
     * {@link #currentUserSystemRole()} 的别名方法。
     */
    public static String getCurrentUserSystemRole() {
        return currentUserSystemRole();
    }

    /**
     * 判断当前用户是否为超级管理员。
     *
     * @return true 表示当前用户拥有 super_admin 角色
     */
    public static boolean isSuperAdmin() {
        return "super_admin".equals(currentUserSystemRole());
    }
}
