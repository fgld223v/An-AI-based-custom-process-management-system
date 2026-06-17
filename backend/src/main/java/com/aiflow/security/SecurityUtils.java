package com.aiflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            return null;
        }
        return currentUser;
    }

    public static Long currentUserId() {
        CurrentUser currentUser = currentUser();
        return currentUser == null ? null : currentUser.getId();
    }

    public static String currentUserSystemRole() {
        CurrentUser currentUser = currentUser();
        return currentUser == null ? null : currentUser.getSystemRole();
    }

    public static boolean isSuperAdmin() {
        return "super_admin".equals(currentUserSystemRole());
    }
}
